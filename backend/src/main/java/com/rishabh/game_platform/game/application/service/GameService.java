package com.rishabh.game_platform.game.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.rishabh.game_platform.game.domain.enums.GameStatus;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.GameState;
import com.rishabh.game_platform.game.domain.model.Move;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.game.domain.ports.GameStateRepository;
import com.rishabh.game_platform.game.engine.core.GameEngine;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GameService {

    private final GameEngine gameEngine;
    private final GameStateRepository gameStateRepository;

    public GameSession createGame(Player host, GameType gameType) {
        GameState initialState = gameEngine.initializeGame();

        GameSession newSession = GameSession.builder()
                .gameId(UUID.randomUUID())
                .gameType(gameType)
                .status(GameStatus.WAITING_FOR_PLAYERS)
                .player1(host)
                .state(initialState)
                .build();

        return gameStateRepository.save(newSession);
    }

    public GameSession joinGame(UUID sessionId, Player guest) {
        GameSession session = gameStateRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Game session not found"));

        if (session.getStatus() != GameStatus.WAITING_FOR_PLAYERS) {
            throw new IllegalStateException("Game is already in progress or finished");
        }

        session.setPlayer2(guest);
        session.setStatus(GameStatus.IN_PROGRESS);

        return gameStateRepository.save(session);
    }

    public GameSession executeMove(UUID sessionId, Player player, Move move) {
        GameSession session = gameStateRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Game session not found"));

        if (session.getStatus() != GameStatus.IN_PROGRESS) {
            throw new IllegalStateException("Game is not in progress");
        }

        if (!gameEngine.isMoveValid(session, move)) {
            throw new IllegalArgumentException("Invalid move");
        }

        GameState newState = gameEngine.executeMove(session, move);
        session.setState(newState);
        session.setStatus(newState.getStatus());
        return gameStateRepository.save(session);
    }
}
