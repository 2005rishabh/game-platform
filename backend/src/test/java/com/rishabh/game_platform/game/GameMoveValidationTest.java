package com.rishabh.game_platform.game;

import com.rishabh.game_platform.game.application.service.GameEventPublisher;
import com.rishabh.game_platform.game.application.service.GameService;
import com.rishabh.game_platform.game.chess.engine.ChessGameEngine;
import com.rishabh.game_platform.game.domain.enums.GameStatus;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.Move;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.game.infrastructure.persistence.memory.InMemoryGameStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GameMoveValidationTest {

    private InMemoryGameStateRepository gameStateRepository;
    private GameEventPublisher gameEventPublisher;
    private GameService gameService;
    private GameSession session;
    private Player whitePlayer;

    @BeforeEach
    void setUp() {
        gameStateRepository = new InMemoryGameStateRepository();
        gameEventPublisher = mock(GameEventPublisher.class);
        gameService = new GameService(new ChessGameEngine(), gameStateRepository, gameEventPublisher);

        whitePlayer = Player.builder()
                .userId(UUID.randomUUID())
                .username("white-player")
                .build();
        Player blackPlayer = Player.builder()
                .userId(UUID.randomUUID())
                .username("black-player")
                .build();

        session = gameService.createGame(whitePlayer, GameType.CHESS);
        session = gameService.joinGame(session.getGameId(), blackPlayer);
    }

    @Test
    @DisplayName("DT-01: Reject an illegal rook move without changing the saved board")
    void rejectsIllegalMoveWithoutCorruptingGameState() {
        String originalBoard = session.getState().getBoardState();
        Move illegalRookMove = Move.builder().from("a1").to("b2").build();

        assertThrows(IllegalArgumentException.class,
                () -> gameService.executeMove(session.getGameId(), whitePlayer, illegalRookMove));

        GameSession persistedSession = gameStateRepository.findById(session.getGameId()).orElseThrow();
        assertEquals(GameStatus.IN_PROGRESS, persistedSession.getStatus());
        assertEquals(originalBoard, persistedSession.getState().getBoardState());
        verify(gameEventPublisher, never()).publishGameEnded(org.mockito.ArgumentMatchers.any());
    }
}
