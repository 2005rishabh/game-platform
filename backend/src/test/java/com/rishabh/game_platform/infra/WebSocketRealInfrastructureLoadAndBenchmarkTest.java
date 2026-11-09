package com.rishabh.game_platform.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rishabh.game_platform.auth.infrastructure.persistence.UserEntity;
import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import com.rishabh.game_platform.game.api.dto.MoveRequest;
import com.rishabh.game_platform.game.application.service.GameService;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.shared.security.JwtService;
import com.rishabh.game_platform.shared.util.IdUtils;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("real")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WebSocketRealInfrastructureLoadAndBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(
        WebSocketRealInfrastructureLoadAndBenchmarkTest.class
    );

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GameService gameService;

    private String getWsEndpoint() {
        return "http://localhost:" + port + "/ws";
    }

    private WebSocketStompClient createStompClient() {
        List<Transport> transports = List.of(
            new WebSocketTransport(new StandardWebSocketClient())
        );
        WebSocketStompClient client = new WebSocketStompClient(
            new SockJsClient(transports)
        );
        client.setMessageConverter(new MappingJackson2MessageConverter());
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        client.setTaskScheduler(scheduler);
        return client;
    }

    private UserEntity createAuthenticatedUser(String username) {
        return userRepository.findByUsername(username).orElseGet(() ->
            userRepository.save(
                UserEntity.builder()
                    .username(username)
                    .password("$2a$10$xyz")
                    .email(username + "@test.com")
                    .eloRating(1200)
                    .build()
            )
        );
    }

    private String generateTokenForUser(String username) {
        return jwtService.generateToken(
            User.builder()
                .username(username)
                .password("pwd")
                .roles("USER")
                .build()
        );
    }

    // --- Correctness Verification ---

    @Test
    @Order(1)
    @DisplayName(
        "WS-01: WebSocket connection with JWT authentication and move broadcast"
    )
    void testWebSocketAuthenticationAndMoveBroadcast() throws Exception {
        String uWhite = "ws_w_" + UUID.randomUUID().toString().substring(0, 6);
        String uBlack = "ws_b_" + UUID.randomUUID().toString().substring(0, 6);

        UserEntity userWhite = createAuthenticatedUser(uWhite);
        UserEntity userBlack = createAuthenticatedUser(uBlack);

        String tokenWhite = generateTokenForUser(uWhite);
        String tokenBlack = generateTokenForUser(uBlack);

        Player white = Player.builder()
            .userId(IdUtils.fromLong(userWhite.getId()))
            .username(uWhite)
            .build();
        Player black = Player.builder()
            .userId(IdUtils.fromLong(userBlack.getId()))
            .username(uBlack)
            .build();

        GameSession session = gameService.createGame(white, GameType.CHESS);
        gameService.joinGame(session.getGameId(), black);

        WebSocketStompClient client = createStompClient();

        StompHeaders headersWhite = new StompHeaders();
        headersWhite.add("Authorization", "Bearer " + tokenWhite);

        StompHeaders headersBlack = new StompHeaders();
        headersBlack.add("Authorization", "Bearer " + tokenBlack);

        StompSession sessionWhite = client
            .connectAsync(
                getWsEndpoint(),
                new WebSocketHttpHeaders(),
                headersWhite,
                new StompSessionHandlerAdapter() {}
            )
            .get(5, TimeUnit.SECONDS);
        StompSession sessionBlack = client
            .connectAsync(
                getWsEndpoint(),
                new WebSocketHttpHeaders(),
                headersBlack,
                new StompSessionHandlerAdapter() {}
            )
            .get(5, TimeUnit.SECONDS);

        assertTrue(sessionWhite.isConnected());
        assertTrue(sessionBlack.isConnected());

        LinkedBlockingQueue<GameSession> whiteUpdates =
            new LinkedBlockingQueue<>();
        LinkedBlockingQueue<GameSession> blackUpdates =
            new LinkedBlockingQueue<>();

        sessionWhite.subscribe(
            "/topic/game/" + session.getGameId(),
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return GameSession.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    whiteUpdates.offer((GameSession) payload);
                }
            }
        );

        sessionBlack.subscribe(
            "/topic/game/" + session.getGameId(),
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return GameSession.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    blackUpdates.offer((GameSession) payload);
                }
            }
        );

        Thread.sleep(500);

        // Submit legal move: 1. e4
        MoveRequest move = new MoveRequest();
        move.setUsername(uWhite);
        move.setFrom("e2");
        move.setTo("e4");

        sessionWhite.send("/app/game/" + session.getGameId() + "/move", move);

        GameSession receivedWhite = whiteUpdates.poll(5, TimeUnit.SECONDS);
        GameSession receivedBlack = blackUpdates.poll(5, TimeUnit.SECONDS);

        assertNotNull(
            receivedWhite,
            "White player must receive move broadcast"
        );
        assertNotNull(
            receivedBlack,
            "Black player must receive move broadcast"
        );
        assertEquals(
            "BLACK",
            receivedWhite.getState().getCurrentTurn(),
            "Turn must switch to BLACK"
        );
        assertTrue(
            receivedWhite.getState().getBoardState().contains("4P3"),
            "Board state must reflect move 1. e4 (FEN 4P3)"
        );

        sessionWhite.disconnect();
        sessionBlack.disconnect();
    }

    // --- Performance Benchmarking ---

    @Test
    @Order(2)
    @DisplayName(
        "WS-BENCH: Progressive Concurrent WebSocket Connection & Latency Benchmark (10, 50, 100 connections)"
    )
    void runWebSocketConnectionLoadBenchmarks() throws Exception {
        log.info("Starting WebSocket Connection & Latency Benchmark Suite...");

        int[] workloads = { 10, 50, 100 };

        for (int concurrency : workloads) {
            log.info(
                "\n======================================================="
            );
            log.info(
                "RUNNING WEBSOCKET BENCHMARK AT CONCURRENCY: {}",
                concurrency
            );
            log.info("=======================================================");

            ExecutorService executor = Executors.newFixedThreadPool(
                concurrency
            );
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(concurrency);
            List<Double> connectionLatencies = Collections.synchronizedList(
                new ArrayList<>(concurrency)
            );
            List<StompSession> activeSessions = Collections.synchronizedList(
                new ArrayList<>(concurrency)
            );
            AtomicInteger failures = new AtomicInteger(0);

            List<String> tokens = new ArrayList<>(concurrency);
            for (int i = 0; i < concurrency; i++) {
                String u = "ws_user_" + concurrency + "_" + i;
                createAuthenticatedUser(u);
                tokens.add(generateTokenForUser(u));
            }

            WebSocketStompClient stompClient = createStompClient();

            long startNs = System.nanoTime();
            for (int i = 0; i < concurrency; i++) {
                final String token = tokens.get(i);
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        StompHeaders headers = new StompHeaders();
                        headers.add("Authorization", "Bearer " + token);

                        long t0 = System.nanoTime();
                        StompSession session = stompClient
                            .connectAsync(
                                getWsEndpoint(),
                                new WebSocketHttpHeaders(),
                                headers,
                                new StompSessionHandlerAdapter() {}
                            )
                            .get(10, TimeUnit.SECONDS);
                        long t1 = System.nanoTime();

                        if (session.isConnected()) {
                            activeSessions.add(session);
                            connectionLatencies.add((t1 - t0) / 1_000_000.0);
                        } else {
                            failures.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            long endNs = System.nanoTime();
            executor.shutdown();

            double totalTimeSec = (endNs - startNs) / 1_000_000_000.0;
            double connectOpsPerSec = activeSessions.size() / totalTimeSec;

            Collections.sort(connectionLatencies);
            double avg = connectionLatencies
                .stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            double p50 = connectionLatencies.isEmpty()
                ? 0
                : connectionLatencies.get(
                      (int) (connectionLatencies.size() * 0.50)
                  );
            double p95 = connectionLatencies.isEmpty()
                ? 0
                : connectionLatencies.get(
                      (int) (connectionLatencies.size() * 0.95)
                  );
            double p99 = connectionLatencies.isEmpty()
                ? 0
                : connectionLatencies.get(
                      (int) (connectionLatencies.size() * 0.99)
                  );
            double failRate = (failures.get() * 100.0) / concurrency;

            System.out.println(
                "\n----------------------------------------------------------------------------------------------------------------"
            );
            System.out.printf(
                "WEBSOCKET CONNECTION BENCHMARK REPORT (Concurrency: %d)%n",
                concurrency
            );
            System.out.println(
                "----------------------------------------------------------------------------------------------------------------"
            );
            System.out.printf(
                "%-26s | %-12s | %-10s | %-10s | %-10s | %-10s | %-10s%n",
                "Metric",
                "Throughput",
                "Avg Latency",
                "p50 Latency",
                "p95 Latency",
                "p99 Latency",
                "Active/Total"
            );
            System.out.println(
                "---------------------------|--------------|------------|-------------|-------------|-------------|-----------"
            );
            System.out.printf(
                "%-26s | %9.1f conn/s| %7.2f ms | %8.2f ms | %8.2f ms | %8.2f ms | %4d / %-4d%n",
                "WS STOMP Handshake+Auth",
                connectOpsPerSec,
                avg,
                p50,
                p95,
                p99,
                activeSessions.size(),
                concurrency
            );
            System.out.println(
                "----------------------------------------------------------------------------------------------------------------\n"
            );

            assertEquals(
                concurrency,
                activeSessions.size(),
                "All concurrent connections must successfully establish"
            );

            // Clean up connections
            for (StompSession s : activeSessions) {
                try {
                    s.disconnect();
                } catch (Exception ignored) {}
            }
            Thread.sleep(500);
        }
    }
}
