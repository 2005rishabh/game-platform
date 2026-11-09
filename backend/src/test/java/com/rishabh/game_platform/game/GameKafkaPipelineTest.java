package com.rishabh.game_platform.game;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rishabh.game_platform.auth.infrastructure.persistence.UserEntity;
import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import com.rishabh.game_platform.game.domain.enums.GameStatus;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.events.GameEndedEvent;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.GameState;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.game.domain.ports.GameStateRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
    properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.properties.security.protocol=PLAINTEXT",
        "spring.kafka.consumer.group-id=game-platform-pipeline-test-group",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.listener.auto-startup=true",
    }
)
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = { "game-ended-topic" })
public class GameKafkaPipelineTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GameStateRepository gameStateRepository;

    @Autowired
    private KafkaListenerEndpointRegistry endpointRegistry;

    @AfterAll
    static void cleanUpEmbeddedKafka() {
        System.clearProperty("spring.embedded.kafka.brokers");
        System.clearProperty("spring.kafka.bootstrap-servers");
    }

    @Test
    @DisplayName(
        "IT-03: Publish mock GameEndedEvent to embedded Kafka topic and verify consumer processing and downstream rating update"
    )
    void testKafkaEventPublishAndConsume() throws Exception {
        String winnerUsername = "kafka-winner-" + UUID.randomUUID();
        String loserUsername = "kafka-loser-" + UUID.randomUUID();

        UserEntity winner = new UserEntity();
        winner.setUsername(winnerUsername);
        winner.setEmail(winnerUsername + "@test.invalid");
        winner.setPassword("unused");
        winner.setEloRating(1200);
        winner.setCreatedAt(LocalDateTime.now());
        userRepository.saveAndFlush(winner);

        UserEntity loser = new UserEntity();
        loser.setUsername(loserUsername);
        loser.setEmail(loserUsername + "@test.invalid");
        loser.setPassword("unused");
        loser.setEloRating(1200);
        loser.setCreatedAt(LocalDateTime.now());
        userRepository.saveAndFlush(loser);

        UUID gameId = UUID.randomUUID();
        GameSession session = GameSession.builder()
            .gameId(gameId)
            .gameType(GameType.CHESS)
            .status(GameStatus.IN_PROGRESS)
            .player1(Player.builder().username(winnerUsername).build())
            .player2(Player.builder().username(loserUsername).build())
            .state(GameState.builder().boardState("start").build())
            .build();
        gameStateRepository.save(session);

        // Ensure consumer container is assigned partitions before producing
        for (MessageListenerContainer container : endpointRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 1);
        }

        GameEndedEvent event = new GameEndedEvent(
            gameId,
            winnerUsername,
            loserUsername,
            "CHECKMATE"
        );

        assertDoesNotThrow(() -> {
            kafkaTemplate
                .send("game-ended-topic", event.gameId().toString(), event)
                .get(5, TimeUnit.SECONDS);
        }, "Kafka template should successfully send the event to the embedded broker");

        // Verify downstream consumption and rating updates
        boolean processed = false;
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            Optional<UserEntity> updatedWinner = userRepository.findByUsername(
                winnerUsername
            );
            if (
                updatedWinner.isPresent() &&
                updatedWinner.get().getEloRating() > 1200
            ) {
                processed = true;
                assertEquals(1216, updatedWinner.get().getEloRating());
                Optional<UserEntity> updatedLoser =
                    userRepository.findByUsername(loserUsername);
                assertTrue(updatedLoser.isPresent());
                assertEquals(1184, updatedLoser.get().getEloRating());
                break;
            }
            Thread.sleep(200);
        }

        assertTrue(
            processed,
            "Kafka consumer should process the event and update player ratings within 15 seconds"
        );
    }
}
