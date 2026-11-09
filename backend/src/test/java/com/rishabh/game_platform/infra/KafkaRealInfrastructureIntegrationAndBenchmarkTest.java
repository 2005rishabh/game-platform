package com.rishabh.game_platform.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rishabh.game_platform.auth.infrastructure.persistence.UserEntity;
import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import com.rishabh.game_platform.game.domain.enums.GameStatus;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.events.GameEndedEvent;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.game.domain.ports.GameStateRepository;
import com.rishabh.game_platform.game.infrastructure.persistence.jpa.SpringDataProcessedGameEventRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("real")
@TestPropertySource(
    properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.group-id=real-kafka-bench-group",
        "spring.kafka.consumer.auto-offset-reset=latest",
    }
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KafkaRealInfrastructureIntegrationAndBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(
        KafkaRealInfrastructureIntegrationAndBenchmarkTest.class
    );
    private static final String TOPIC = "game-ended-topic";

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GameStateRepository gameStateRepository;

    @Autowired
    private SpringDataProcessedGameEventRepository processedEventRepository;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @BeforeAll
    void setupTopicAndListener() throws Exception {
        // Ensure topic exists on real broker
        try (
            AdminClient admin = AdminClient.create(
                Map.of(
                    AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                    "localhost:9092"
                )
            )
        ) {
            admin
                .createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1)))
                .all()
                .get(5, TimeUnit.SECONDS);
            log.info("Topic {} confirmed on broker", TOPIC);
        } catch (Exception ignored) {
            // Topic already exists
        }

        // Wait for Spring Kafka listener container to assign partitions
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            int retries = 0;
            while (
                container.getAssignedPartitions() == null ||
                container.getAssignedPartitions().isEmpty()
            ) {
                Thread.sleep(500);
                if (++retries > 20) break;
            }
            log.info(
                "Kafka listener container assigned partitions: {}",
                container.getAssignedPartitions()
            );
        }
    }

    private UserEntity createPlayer(String username, int elo) {
        return userRepository.findByUsername(username).orElseGet(() ->
            userRepository.save(
                UserEntity.builder()
                    .username(username)
                    .password("pwd")
                    .email(username + "@test.com")
                    .eloRating(elo)
                    .build()
            )
        );
    }

    private GameSession createSession(UUID gameId, String w, String l) {
        GameSession s = GameSession.builder()
            .gameId(gameId)
            .gameType(GameType.CHESS)
            .status(GameStatus.IN_PROGRESS)
            .player1(Player.builder().username(w).build())
            .player2(Player.builder().username(l).build())
            .build();
        return gameStateRepository.save(s);
    }

    // --- Correctness Verification ---

    @Test
    @Order(1)
    @DisplayName(
        "KAFKA-01: End-to-end publish, consume, Elo update, and event persistence"
    )
    void testEndToEndPublishAndConsume() throws Exception {
        UUID gameId = UUID.randomUUID();
        String win = "k_win_" + gameId.toString().substring(0, 8);
        String los = "k_los_" + gameId.toString().substring(0, 8);

        createPlayer(win, 1200);
        createPlayer(los, 1200);
        createSession(gameId, win, los);

        GameEndedEvent event = new GameEndedEvent(
            gameId,
            win,
            los,
            "CHECKMATE"
        );
        kafkaTemplate
            .send(TOPIC, gameId.toString(), event)
            .get(5, TimeUnit.SECONDS);

        // Await consumption and database commit
        boolean processed = awaitEventProcessed(gameId, Duration.ofSeconds(10));
        assertTrue(
            processed,
            "GameEndedEvent must be processed and persisted within 10 seconds"
        );

        UserEntity updatedWin = userRepository
            .findByUsername(win)
            .orElseThrow();
        UserEntity updatedLos = userRepository
            .findByUsername(los)
            .orElseThrow();

        assertEquals(1216, updatedWin.getEloRating());
        assertEquals(1184, updatedLos.getEloRating());
        assertTrue(processedEventRepository.findById(gameId).isPresent());
    }

    @Test
    @Order(2)
    @DisplayName(
        "KAFKA-02: Duplicate delivery via Kafka does not apply ELO twice"
    )
    void testDuplicateDeliveryDeduplication() throws Exception {
        UUID gameId = UUID.randomUUID();
        String win = "k_dup_w_" + gameId.toString().substring(0, 8);
        String los = "k_dup_l_" + gameId.toString().substring(0, 8);

        createPlayer(win, 1200);
        createPlayer(los, 1200);
        createSession(gameId, win, los);

        GameEndedEvent event = new GameEndedEvent(
            gameId,
            win,
            los,
            "CHECKMATE"
        );

        // Send 1st delivery
        kafkaTemplate
            .send(TOPIC, gameId.toString(), event)
            .get(5, TimeUnit.SECONDS);
        assertTrue(awaitEventProcessed(gameId, Duration.ofSeconds(10)));

        // Send 2nd (duplicate) delivery
        kafkaTemplate
            .send(TOPIC, gameId.toString(), event)
            .get(5, TimeUnit.SECONDS);
        // Send 3rd (duplicate) delivery
        kafkaTemplate
            .send(TOPIC, gameId.toString(), event)
            .get(5, TimeUnit.SECONDS);

        Thread.sleep(2000); // Allow Kafka to process duplicates

        UserEntity updatedWin = userRepository
            .findByUsername(win)
            .orElseThrow();
        UserEntity updatedLos = userRepository
            .findByUsername(los)
            .orElseThrow();

        assertEquals(
            1216,
            updatedWin.getEloRating(),
            "Winner Elo must remain 1216 despite 3 deliveries"
        );
        assertEquals(
            1184,
            updatedLos.getEloRating(),
            "Loser Elo must remain 1184 despite 3 deliveries"
        );
        assertTrue(processedEventRepository.findById(gameId).isPresent());
    }

    // --- Performance Benchmarking ---

    @Test
    @Order(3)
    @DisplayName(
        "KAFKA-BENCH: Kafka Throughput and End-to-End Latency Benchmarks (10, 50, 100 workloads)"
    )
    void runKafkaPerformanceBenchmarks() throws Exception {
        log.info("Starting Kafka Real Infrastructure Benchmark Suite...");

        int[] workloads = { 10, 50, 100 };

        for (int workload : workloads) {
            log.info(
                "\n======================================================="
            );
            log.info("RUNNING KAFKA BENCHMARK WORKLOAD: {} EVENTS", workload);
            log.info("=======================================================");

            List<UUID> gameIds = new ArrayList<>(workload);
            List<GameEndedEvent> events = new ArrayList<>(workload);
            Map<UUID, Long> sendTimestamps = new ConcurrentHashMap<>();

            // Pre-create users and sessions
            for (int i = 0; i < workload; i++) {
                UUID gid = UUID.randomUUID();
                String w =
                    "kw_w_" +
                    workload +
                    "_" +
                    i +
                    "_" +
                    gid.toString().substring(0, 4);
                String l =
                    "kw_l_" +
                    workload +
                    "_" +
                    i +
                    "_" +
                    gid.toString().substring(0, 4);
                createPlayer(w, 1200);
                createPlayer(l, 1200);
                createSession(gid, w, l);
                gameIds.add(gid);
                events.add(new GameEndedEvent(gid, w, l, "CHECKMATE"));
            }

            // 1. Publishing Benchmark
            long publishStartNs = System.nanoTime();
            for (GameEndedEvent event : events) {
                sendTimestamps.put(event.gameId(), System.currentTimeMillis());
                kafkaTemplate.send(TOPIC, event.gameId().toString(), event);
            }
            kafkaTemplate.flush();
            long publishEndNs = System.nanoTime();

            double publishSec =
                (publishEndNs - publishStartNs) / 1_000_000_000.0;
            double publishThroughput = workload / publishSec;

            // 2. Consumption & End-to-End Latency Measurement
            long consumeStartNs = System.nanoTime();
            List<Double> e2eLatencies = new ArrayList<>(workload);
            AtomicInteger processedCount = new AtomicInteger(0);

            long timeoutLimitMs = System.currentTimeMillis() + 45_000;
            for (UUID gid : gameIds) {
                while (
                    !processedEventRepository.existsById(gid) &&
                    System.currentTimeMillis() < timeoutLimitMs
                ) {
                    Thread.sleep(20);
                }
                if (processedEventRepository.existsById(gid)) {
                    long now = System.currentTimeMillis();
                    Long sentAt = sendTimestamps.get(gid);
                    if (sentAt != null) {
                        e2eLatencies.add((double) (now - sentAt));
                    }
                    processedCount.incrementAndGet();
                }
            }
            long consumeEndNs = System.nanoTime();

            double consumeSec =
                (consumeEndNs - consumeStartNs) / 1_000_000_000.0;
            double consumeThroughput = processedCount.get() / consumeSec;

            Collections.sort(e2eLatencies);
            double avg = e2eLatencies
                .stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            double p50 = e2eLatencies.isEmpty()
                ? 0
                : e2eLatencies.get((int) (e2eLatencies.size() * 0.50));
            double p95 = e2eLatencies.isEmpty()
                ? 0
                : e2eLatencies.get((int) (e2eLatencies.size() * 0.95));
            double p99 = e2eLatencies.isEmpty()
                ? 0
                : e2eLatencies.get((int) (e2eLatencies.size() * 0.99));
            double failRate =
                ((workload - processedCount.get()) * 100.0) / workload;

            System.out.println(
                "\n----------------------------------------------------------------------------------------------------------------"
            );
            System.out.printf(
                "KAFKA PROCESSING BENCHMARK REPORT (Events: %d)%n",
                workload
            );
            System.out.println(
                "----------------------------------------------------------------------------------------------------------------"
            );
            System.out.printf(
                "%-20s | %-15s | %-15s | %-10s | %-10s | %-10s | %-10s%n",
                "Workload",
                "Publish Thruput",
                "Consume Thruput",
                "Avg E2E Lat",
                "p50 E2E Lat",
                "p95 E2E Lat",
                "p99 E2E Lat"
            );
            System.out.println(
                "---------------------|-----------------|-----------------|------------|-------------|-------------|-----------"
            );
            System.out.printf(
                "%-20s | %10.1f ev/s | %10.1f ev/s | %7.2f ms | %8.2f ms | %8.2f ms | %8.2f ms%n",
                workload + " GameEndedEvents",
                publishThroughput,
                consumeThroughput,
                avg,
                p50,
                p95,
                p99
            );
            System.out.println(
                "----------------------------------------------------------------------------------------------------------------\n"
            );

            assertEquals(
                workload,
                processedCount.get(),
                "All events must be processed"
            );
        }
    }

    private boolean awaitEventProcessed(UUID gameId, Duration timeout)
        throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (processedEventRepository.existsById(gameId)) {
                return true;
            }
            Thread.sleep(50);
        }
        return processedEventRepository.existsById(gameId);
    }
}
