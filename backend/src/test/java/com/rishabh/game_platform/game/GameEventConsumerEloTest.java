package com.rishabh.game_platform.game;

import com.rishabh.game_platform.auth.infrastructure.persistence.UserEntity;
import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import com.rishabh.game_platform.game.application.service.GameEventConsumer;
import com.rishabh.game_platform.game.domain.enums.GameStatus;
import com.rishabh.game_platform.game.domain.events.GameEndedEvent;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.game.domain.ports.GameStateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameEventConsumerEloTest {

    @Test
    @DisplayName("UT-01: Actual consumer awards a favorite a smaller win and an upset a larger win")
    void adjustsRatingsForFavoriteAndUpsetWins() {
        assertWinRatings(1600, 1400, 1608, 1392);
        assertWinRatings(1400, 1600, 1424, 1576);
    }

    @Test
    @DisplayName("UT-01: Actual consumer applies symmetric draw scores")
    void appliesDrawRatingsSymmetrically() {
        UserEntity winner = user("winner", 1500);
        UserEntity loser = user("loser", 1500);
        Fixture fixture = fixture(winner, loser);

        fixture.consumer.consumeGameEndedEvent(
                new GameEndedEvent(fixture.gameId, "winner", "loser", "DRAW"));

        assertEquals(1500, winner.getEloRating());
        assertEquals(1500, loser.getEloRating());
        assertEquals(GameStatus.DRAW, fixture.session.getStatus());
        verify(fixture.userRepository).save(winner);
        verify(fixture.userRepository).save(loser);
    }

    @Test
    @DisplayName("DT-03: Redelivering the same game result must not apply ELO twice")
    void ignoresDuplicateGameEndedEvent() {
        UserEntity winner = user("winner", 1200);
        UserEntity loser = user("loser", 1200);
        Fixture fixture = fixture(winner, loser);
        GameEndedEvent event = new GameEndedEvent(fixture.gameId, "winner", "loser", "CHECKMATE");

        fixture.consumer.consumeGameEndedEvent(event);
        fixture.consumer.consumeGameEndedEvent(event);

        assertEquals(1216, winner.getEloRating());
        assertEquals(1184, loser.getEloRating());
    }

    private void assertWinRatings(int winnerRating, int loserRating, int expectedWinner, int expectedLoser) {
        UserEntity winner = user("winner", winnerRating);
        UserEntity loser = user("loser", loserRating);
        Fixture fixture = fixture(winner, loser);

        fixture.consumer.consumeGameEndedEvent(
                new GameEndedEvent(fixture.gameId, "winner", "loser", "CHECKMATE"));

        assertEquals(expectedWinner, winner.getEloRating());
        assertEquals(expectedLoser, loser.getEloRating());
        verify(fixture.userRepository).save(winner);
        verify(fixture.userRepository).save(loser);
    }

    private Fixture fixture(UserEntity winner, UserEntity loser) {
        GameStateRepository gameStateRepository = mock(GameStateRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID gameId = UUID.randomUUID();
        GameSession session = GameSession.builder()
                .gameId(gameId)
                .status(GameStatus.IN_PROGRESS)
                .player1(Player.builder().username("winner").build())
                .player2(Player.builder().username("loser").build())
                .build();

        when(gameStateRepository.findById(gameId)).thenReturn(Optional.of(session));
        when(gameStateRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByUsername("winner")).thenReturn(Optional.of(winner));
        when(userRepository.findByUsername("loser")).thenReturn(Optional.of(loser));

        return new Fixture(new GameEventConsumer(gameStateRepository, userRepository),
                userRepository, gameId, session);
    }

    private UserEntity user(String username, int rating) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEloRating(rating);
        return user;
    }

    private record Fixture(GameEventConsumer consumer,
                           UserRepository userRepository,
                           UUID gameId,
                           GameSession session) {
    }
}
