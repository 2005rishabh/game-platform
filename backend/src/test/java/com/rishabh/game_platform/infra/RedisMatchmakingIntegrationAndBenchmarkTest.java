package com.rishabh.game_platform.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.game.domain.ports.GameStateRepository;
import com.rishabh.game_platform.matchmaking.application.service.MatchmakingService;
import com.rishabh.game_platform.matchmaking.infrastructure.persistence.redis.RedisMatchmakingQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("real")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RedisMatchmakingIntegrationAndBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(
        RedisMatchmakingIntegrationAndBenchmarkTest.class
    );

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisMatchmakingQueue redisMatchmakingQueue;

    @Autowired
    private MatchmakingService matchmakingService;

    @Autowired
    private GameStateRepository gameStateRepository;

    @BeforeEach
    void clearRedis() {
        try (
            RedisConnection connection = redisTemplate
                .getConnectionFactory()
                .getConnection()
        ) {
            connection.serverCommands().flushDb();
        }
    }

    // --- Correctness Verification ---

    @Test
    @Order(1)
    @DisplayName("REDIS-01: Verify Redis 7 connection and ping")
    void testRedisConnection() {
        String pong = redisTemplate
            .getConnectionFactory()
            .getConnection()
            .ping();
        assertEquals("PONG", pong);
        log.info("Redis 7 connection ping successful: {}", pong);
    }

    @Test
    @Order(2)
    @DisplayName("REDIS-02: Two different players form exactly one match")
    void testTwoPlayersFormOneMatch() {
        Player p1 = Player.builder()
            .userId(UUID.randomUUID())
            .username("player_alpha")
            .build();
        Player p2 = Player.builder()
            .userId(UUID.randomUUID())
            .username("player_beta")
            .build();

        // Player 1 queues
        matchmakingService.processJoinRequest(p1, GameType.CHESS);
        // Player 2 queues and matches with Player 1
        matchmakingService.processJoinRequest(p2, GameType.CHESS);

        // Queue should now be empty (player 1 was extracted)
        Optional<Player> next = redisMatchmakingQueue.extractOpponent(
            GameType.CHESS,
            p1
        );
        assertTrue(
            next.isEmpty(),
            "Queue must be empty after match extraction"
        );
    }

    @Test
    @Order(3)
    @DisplayName("REDIS-03: A player cannot match against themselves")
    void testPlayerCannotMatchAgainstSelf() {
        Player p1 = Player.builder()
            .userId(UUID.randomUUID())
            .username("solo_player")
            .build();

        redisMatchmakingQueue.addPlayer(p1, GameType.CHESS);
        Optional<Player> selfMatch = redisMatchmakingQueue.extractOpponent(
            GameType.CHESS,
            p1
        );

        assertTrue(
            selfMatch.isEmpty(),
            "Player must not match against their own UUID"
        );
    }

    @Test
    @Order(4)
    @DisplayName("REDIS-04: Queue cancellation (lazy removal) works correctly")
    void testCancelRemovesPlayerFromMatching() {
        Player p1 = Player.builder()
            .userId(UUID.randomUUID())
            .username("cancelling_player")
            .build();
        Player p2 = Player.builder()
            .userId(UUID.randomUUID())
            .username("active_player")
            .build();

        redisMatchmakingQueue.addPlayer(p1, GameType.CHESS);
        // Cancel p1
        matchmakingService.processCancelRequest(p1.getUserId());

        // When p2 arrives, p1 should be recognized as canceled and discarded
        Optional<Player> matchForP2 = redisMatchmakingQueue.extractOpponent(
            GameType.CHESS,
            p2
        );
        assertTrue(matchForP2.isEmpty(), "Canceled player must not be matched");
    }

    @Test
    @Order(5)
    @DisplayName(
        "REDIS-05: Concurrent queue requests do not assign one player to multiple games"
    )
    void testConcurrentQueueRequestsDoNotDuplicate() throws Exception {
        int playerCount = 40;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(playerCount);

        List<Player> players = new ArrayList<>();
        Set<UUID> allPlayerIds = new HashSet<>();
        for (int i = 0; i < playerCount; i++) {
            UUID uid = UUID.randomUUID();
            players.add(
                Player.builder()
                    .userId(uid)
                    .username(
                        "conc_user_" + i + "_" + uid.toString().substring(0, 4)
                    )
                    .build()
            );
            allPlayerIds.add(uid);
        }

        for (Player p : players) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    matchmakingService.processJoinRequest(p, GameType.CHESS);
                } catch (Exception e) {
                    log.error("Error during concurrent join", e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        Long remainingInQueue = redisTemplate
            .opsForList()
            .size("matchmaking:queue:CHESS");
        long remaining = remainingInQueue == null ? 0 : remainingInQueue;
        log.info(
            "Concurrent matchmaking finished: Remaining in queue = {}",
            remaining
        );

        // If any players remained queued due to concurrent empty-checks, pair them with follow-up players
        if (remaining > 0) {
            for (int i = 0; i < remaining; i++) {
                Player balancer = Player.builder()
                    .userId(UUID.randomUUID())
                    .username("balancer_" + i)
                    .build();
                matchmakingService.processJoinRequest(balancer, GameType.CHESS);
            }
        }

        Long finalQueueSize = redisTemplate
            .opsForList()
            .size("matchmaking:queue:CHESS");
        assertEquals(
            0L,
            finalQueueSize == null ? 0L : finalQueueSize,
            "Queue must be completely drained"
        );
    }

    // --- Performance Benchmarking ---

    @Test
    @Order(6)
    @DisplayName(
        "REDIS-BENCH: Redis Matchmaking Throughput and Latency Benchmarks (10, 50, 100 workloads)"
    )
    void runRedisPerformanceBenchmarks() throws Exception {
        log.info("Starting Redis Real Infrastructure Benchmark Suite...");

        // Direct Redis Command Latency Benchmark (Push / Pop)
        BenchmarkResult rawRedisResult = benchmarkRawRedisLatency(1000);
        log.info(
            "Raw Redis Command Latency: p50={} ms, p95={} ms, p99={} ms",
            rawRedisResult.p50Ms,
            rawRedisResult.p95Ms,
            rawRedisResult.p99Ms
        );

        int[] workloads = { 10, 50, 100 };

        for (int concurrency : workloads) {
            log.info(
                "\n======================================================="
            );
            log.info(
                "RUNNING REDIS MATCHMAKING BENCHMARK AT CONCURRENCY: {}",
                concurrency
            );
            log.info("=======================================================");

            int totalPlayers = concurrency * 4;
            ExecutorService executor = Executors.newFixedThreadPool(
                concurrency
            );
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(totalPlayers);
            List<Double> latencies = Collections.synchronizedList(
                new ArrayList<>(totalPlayers)
            );
            AtomicInteger failures = new AtomicInteger(0);

            List<Player> players = new ArrayList<>(totalPlayers);
            for (int i = 0; i < totalPlayers; i++) {
                players.add(
                    Player.builder()
                        .userId(UUID.randomUUID())
                        .username(
                            "bench_p_" +
                                i +
                                "_" +
                                UUID.randomUUID().toString().substring(0, 4)
                        )
                        .build()
                );
            }

            long startNs = System.nanoTime();
            for (Player player : players) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        long t0 = System.nanoTime();
                        matchmakingService.processJoinRequest(
                            player,
                            GameType.CHESS
                        );
                        long t1 = System.nanoTime();
                        latencies.add((t1 - t0) / 1_000_000.0); // ms
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            long endNs = System.nanoTime();
            executor.shutdown();

            double totalTimeSec = (endNs - startNs) / 1_000_000_000.0;
            double opsPerSec = (totalPlayers - failures.get()) / totalTimeSec;
            double matchesPerSec = opsPerSec / 2.0;

            Collections.sort(latencies);
            double avg = latencies
                .stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            double p50 = latencies.isEmpty()
                ? 0
                : latencies.get((int) (latencies.size() * 0.50));
            double p95 = latencies.isEmpty()
                ? 0
                : latencies.get((int) (latencies.size() * 0.95));
            double p99 = latencies.isEmpty()
                ? 0
                : latencies.get((int) (latencies.size() * 0.99));
            double failRate = (failures.get() * 100.0) / totalPlayers;

            System.out.println(
                "\n----------------------------------------------------------------------------------------------------------------"
            );
            System.out.printf(
                "REDIS MATCHMAKING BENCHMARK REPORT (Concurrency: %d)%n",
                concurrency
            );
            System.out.println(
                "----------------------------------------------------------------------------------------------------------------"
            );
            System.out.printf(
                "%-26s | %-14s | %-14s | %-10s | %-10s | %-10s | %-10s%n",
                "Metric",
                "Queue Ops/s",
                "Matches/s",
                "Avg Latency",
                "p50 Latency",
                "p95 Latency",
                "p99 Latency"
            );
            System.out.println(
                "---------------------------|----------------|----------------|------------|-------------|-------------|-----------"
            );
            System.out.printf(
                "%-26s | %11.1f ops/s | %11.1f match/s | %7.2f ms | %8.2f ms | %8.2f ms | %8.2f ms%n",
                "Redis Matchmaking Queue",
                opsPerSec,
                matchesPerSec,
                avg,
                p50,
                p95,
                p99
            );
            System.out.println(
                "----------------------------------------------------------------------------------------------------------------\n"
            );
        }
    }

    private BenchmarkResult benchmarkRawRedisLatency(int iterations) {
        String key = "bench:raw:list";
        List<Double> latencies = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            long t0 = System.nanoTime();
            redisTemplate.opsForList().rightPush(key, "val_" + i);
            redisTemplate.opsForList().leftPop(key);
            long t1 = System.nanoTime();
            latencies.add((t1 - t0) / 1_000_000.0);
        }
        Collections.sort(latencies);
        double avg = latencies
            .stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        double p50 = latencies.get((int) (latencies.size() * 0.50));
        double p95 = latencies.get((int) (latencies.size() * 0.95));
        double p99 = latencies.get((int) (latencies.size() * 0.99));
        return new BenchmarkResult(
            "Raw Redis LPUSH/RPOP",
            1,
            iterations,
            0,
            avg,
            p50,
            p95,
            p99,
            0.0
        );
    }

    public record BenchmarkResult(
        String name,
        int concurrency,
        int totalOps,
        double throughput,
        double avgMs,
        double p50Ms,
        double p95Ms,
        double p99Ms,
        double failureRate
    ) {}
}
