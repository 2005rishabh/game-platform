package com.rishabh.game_platform.game;

import com.rishabh.game_platform.auth.infrastructure.persistence.UserEntity;
import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import com.rishabh.game_platform.game.application.service.GameService;
import com.rishabh.game_platform.game.domain.enums.GameStatus;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.Move;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.game.domain.ports.GameStateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.properties.security.protocol=PLAINTEXT"
})
@EmbeddedKafka(
        partitions = 1,
        topics = "game-ended-topic",
        brokerProperties = "offsets.topic.num.partitions=1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GameLifecycleKafkaIntegrationTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private GameStateRepository gameStateRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Test
    @DisplayName("IT-03 / E2E-02: Checkmate event flows through Kafka and applies both players' ELO changes")
    void checkmateEventUpdatesGameAndPlayerRatings() {
        ContainerTestUtils.waitForAssignment(
                kafkaListenerEndpointRegistry.getListenerContainers().iterator().next(), 1);

        String suffix = UUID.randomUUID().toString();
        UserEntity whiteUser = userRepository.saveAndFlush(
                createUser("white-" + suffix, "white-" + suffix + "@test.invalid"));
        UserEntity blackUser = userRepository.saveAndFlush(
                createUser("black-" + suffix, "black-" + suffix + "@test.invalid"));

        Player whitePlayer = Player.builder()
                .userId(UUID.randomUUID())
                .username(whiteUser.getUsername())
                .build();
        Player blackPlayer = Player.builder()
                .userId(UUID.randomUUID())
                .username(blackUser.getUsername())
                .build();

        GameSession session = gameService.createGame(whitePlayer, GameType.CHESS);
        gameService.joinGame(session.getGameId(), blackPlayer);

        playMove(session.getGameId(), whitePlayer, "f2", "f3");
        playMove(session.getGameId(), blackPlayer, "e7", "e5");
        playMove(session.getGameId(), whitePlayer, "g2", "g4");
        GameSession endedGame = playMove(session.getGameId(), blackPlayer, "d8", "h4");

        assertEquals(GameStatus.BLACK_WON, endedGame.getStatus());
        await().untilAsserted(() -> {
            assertEquals(1216, userRepository.findByUsername(blackUser.getUsername())
                    .orElseThrow().getEloRating());
            assertEquals(1184, userRepository.findByUsername(whiteUser.getUsername())
                    .orElseThrow().getEloRating());
            assertEquals(GameStatus.BLACK_WON,
                    gameStateRepository.findById(session.getGameId()).orElseThrow().getStatus());
        });
    }

    private UserEntity createUser(String username, String email) {
        return UserEntity.builder()
                .username(username)
                .email(email)
                .password("test-password")
                .eloRating(1200)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private GameSession playMove(UUID gameId, Player player, String from, String to) {
        return gameService.executeMove(gameId, player,
                Move.builder().from(from).to(to).build());
    }
}
