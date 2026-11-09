package com.rishabh.game_platform.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rishabh.game_platform.auth.infrastructure.persistence.UserEntity;
import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import com.rishabh.game_platform.game.api.dto.MoveRequest;
import com.rishabh.game_platform.game.application.service.GameService;
import com.rishabh.game_platform.game.domain.enums.GameStatus;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.events.GameEndedEvent;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.game.domain.ports.GameStateRepository;
import com.rishabh.game_platform.game.infrastructure.persistence.jpa.GameSessionEntity;
import com.rishabh.game_platform.game.infrastructure.persistence.jpa.SpringDataGameSessionRepository;
import com.rishabh.game_platform.game.infrastructure.persistence.jpa.SpringDataProcessedGameEventRepository;
import com.rishabh.game_platform.matchmaking.application.service.MatchmakingService;
import com.rishabh.game_platform.shared.security.JwtService;
import com.rishabh.game_platform.shared.util.IdUtils;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("real")
@TestPropertySource(
    properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.group-id=e2e-real-workflow-group",
        "spring.kafka.consumer.auto-offset-reset=latest",
    }
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FullEndToEndWorkflowRealInfraTest {

    private static final Logger log = LoggerFactory.getLogger(
        FullEndToEndWorkflowRealInfraTest.class
    );
    private static final String TOPIC = "game-ended-topic";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MatchmakingService matchmakingService;

    @Autowired
    private GameService gameService;

    @Autowired
    private GameStateRepository gameStateRepository;

    @Autowired
    private SpringDataGameSessionRepository springDataGameSessionRepository;

    @Autowired
    private SpringDataProcessedGameEventRepository processedEventRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeAll
    void setupTopicAndListener() throws Exception {
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
        } catch (Exception ignored) {}

        for (MessageListenerContainer container : registry.getListenerContainers()) {
            int retries = 0;
            while (
                container.getAssignedPartitions() == null ||
                container.getAssignedPartitions().isEmpty()
            ) {
                Thread.sleep(500);
                if (++retries > 20) break;
            }
        }
    }

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

    @Test
    @Order(1)
    @DisplayName(
        "E2E-01: Complete Distributed Workflow: PostgreSQL Auth -> Redis Matchmaking -> WebSocket Gameplay -> Kafka Async Event -> PostgreSQL Idempotent Elo Update"
    )
    void testCompleteDistributedWorkflow() throws Exception {
        log.info(
            "Starting Full End-to-End Real Infrastructure Distributed Workflow Test..."
        );

        // 1. Two users authenticate in PostgreSQL
        String aliceName =
            "e2e_alice_" + UUID.randomUUID().toString().substring(0, 6);
        String bobName =
            "e2e_bob_" + UUID.randomUUID().toString().substring(0, 6);

        UserEntity alice = userRepository.save(
            UserEntity.builder()
                .username(aliceName)
                .password("password123")
                .email(aliceName + "@chess.org")
                .eloRating(1200)
                .build()
        );

        UserEntity bob = userRepository.save(
            UserEntity.builder()
                .username(bobName)
                .password("password123")
                .email(bobName + "@chess.org")
                .eloRating(1200)
                .build()
        );

        String aliceToken = jwtService.generateToken(
            User.builder()
                .username(aliceName)
                .password("p")
                .roles("USER")
                .build()
        );
        String bobToken = jwtService.generateToken(
            User.builder().username(bobName).password("p").roles("USER").build()
        );

        // 2. Both users enter Redis matchmaking queue
        Player playerAlice = Player.builder()
            .userId(IdUtils.fromLong(alice.getId()))
            .username(aliceName)
            .build();
        Player playerBob = Player.builder()
            .userId(IdUtils.fromLong(bob.getId()))
            .username(bobName)
            .build();

        matchmakingService.processJoinRequest(playerAlice, GameType.CHESS);
        matchmakingService.processJoinRequest(playerBob, GameType.CHESS);

        // 3. Redis pairs the users and creates GameSession in PostgreSQL
        GameSessionEntity entity = null;
        for (int i = 0; i < 25; i++) {
            List<GameSessionEntity> all =
                springDataGameSessionRepository.findAll();
            for (GameSessionEntity s : all) {
                if (
                    s.getPlayer1Username() != null &&
                    s.getPlayer2Username() != null
                ) {
                    if (
                        (aliceName.equals(s.getPlayer1Username()) &&
                            bobName.equals(s.getPlayer2Username())) ||
                        (bobName.equals(s.getPlayer1Username()) &&
                            aliceName.equals(s.getPlayer2Username()))
                    ) {
                        entity = s;
                        break;
                    }
                }
            }
            if (entity != null) break;
            Thread.sleep(200);
        }

        assertNotNull(
            entity,
            "GameSessionEntity must be persisted in PostgreSQL by Redis matchmaking"
        );
        UUID sessionId = entity.getGameId();
        log.info(
            "Redis Matchmaking successfully paired players into GameSession: {}",
            sessionId
        );

        boolean aliceIsWhite = aliceName.equals(entity.getPlayer1Username());
        String whiteUser = aliceIsWhite ? aliceName : bobName;
        String blackUser = aliceIsWhite ? bobName : aliceName;
        String whiteToken = aliceIsWhite ? aliceToken : bobToken;
        String blackToken = aliceIsWhite ? bobToken : aliceToken;

        // 4. Both users connect through WebSocket
        WebSocketStompClient stompClient = createStompClient();
        String wsUrl = "http://localhost:" + port + "/ws";

        StompHeaders hWhite = new StompHeaders();
        hWhite.add("Authorization", "Bearer " + whiteToken);
        StompHeaders hBlack = new StompHeaders();
        hBlack.add("Authorization", "Bearer " + blackToken);

        StompSession wsWhite = stompClient
            .connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                hWhite,
                new StompSessionHandlerAdapter() {}
            )
            .get(5, TimeUnit.SECONDS);
        StompSession wsBlack = stompClient
            .connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                hBlack,
                new StompSessionHandlerAdapter() {}
            )
            .get(5, TimeUnit.SECONDS);

        LinkedBlockingQueue<GameSession> whiteQueue =
            new LinkedBlockingQueue<>();
        LinkedBlockingQueue<GameSession> blackQueue =
            new LinkedBlockingQueue<>();

        wsWhite.subscribe(
            "/topic/game/" + sessionId,
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return GameSession.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    whiteQueue.offer((GameSession) payload);
                }
            }
        );

        wsBlack.subscribe(
            "/topic/game/" + sessionId,
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return GameSession.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    blackQueue.offer((GameSession) payload);
                }
            }
        );

        Thread.sleep(500);

        // 5. White player submits legal move 1. e4
        MoveRequest m1 = new MoveRequest();
        m1.setUsername(whiteUser);
        m1.setFrom("e2");
        m1.setTo("e4");
        wsWhite.send("/app/game/" + sessionId + "/move", m1);

        GameSession updateWhite = whiteQueue.poll(5, TimeUnit.SECONDS);
        GameSession updateBlack = blackQueue.poll(5, TimeUnit.SECONDS);

        assertNotNull(
            updateWhite,
            "White should receive WebSocket state broadcast"
        );
        assertNotNull(
            updateBlack,
            "Black should receive WebSocket state broadcast"
        );
        assertEquals(
            "BLACK",
            updateWhite.getState().getCurrentTurn(),
            "Turn must switch to BLACK"
        );
        assertTrue(
            updateWhite.getState().getBoardState().contains("4P3"),
            "Board state must reflect move 1. e4 (FEN 4P3)"
        );

        // 6. Black resigns via WebSocket, triggering game conclusion and Kafka publication
        MoveRequest resignReq = new MoveRequest();
        resignReq.setUsername(blackUser);
        wsBlack.send("/app/game/" + sessionId + "/resign", resignReq);

        // 7. Verify GameService concludes game and publishes GameEndedEvent to Kafka
        // Wait for Kafka Consumer to process event and persist Elo updates in PostgreSQL
        long deadline = System.currentTimeMillis() + 15_000;
        while (
            !processedEventRepository.existsById(sessionId) &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(100);
        }

        assertTrue(
            processedEventRepository.existsById(sessionId),
            "Processed event record must exist in PostgreSQL"
        );

        UserEntity finalWhite = userRepository
            .findByUsername(whiteUser)
            .orElseThrow();
        UserEntity finalBlack = userRepository
            .findByUsername(blackUser)
            .orElseThrow();

        assertEquals(
            1216,
            finalWhite.getEloRating(),
            "White winner rating must be updated to 1216 in PostgreSQL"
        );
        assertEquals(
            1184,
            finalBlack.getEloRating(),
            "Black loser rating must be updated to 1184 in PostgreSQL"
        );

        // 8. Deliver Duplicate GameEndedEvent via Kafka to verify exactly-once business effect
        GameEndedEvent duplicateEvent = new GameEndedEvent(
            sessionId,
            whiteUser,
            blackUser,
            "RESIGNATION"
        );
        kafkaTemplate
            .send(TOPIC, sessionId.toString(), duplicateEvent)
            .get(5, TimeUnit.SECONDS);

        Thread.sleep(2000); // Allow Kafka consumer to process duplicate

        UserEntity postDupWhite = userRepository
            .findByUsername(whiteUser)
            .orElseThrow();
        UserEntity postDupBlack = userRepository
            .findByUsername(blackUser)
            .orElseThrow();

        assertEquals(
            1216,
            postDupWhite.getEloRating(),
            "White rating must remain 1216 after duplicate Kafka delivery"
        );
        assertEquals(
            1184,
            postDupBlack.getEloRating(),
            "Black rating must remain 1184 after duplicate Kafka delivery"
        );

        wsWhite.disconnect();
        wsBlack.disconnect();

        log.info(
            "Full End-to-End Distributed Workflow Test successfully verified all 12 steps!"
        );
    }
}
