package com.rishabh.game_platform.game.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.rishabh.game_platform.game.domain.enums.GameStatus;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.events.GameEndedEvent;
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
    private final GameEventPublisher gameEventPublisher;

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
    if (sessionId == null || player == null || move == null) {
        throw new IllegalArgumentException("SessionId, player, and move must not be null");
    }

    GameSession session = gameStateRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Game session not found"));

    if (session.getStatus() != GameStatus.IN_PROGRESS) {
        throw new IllegalStateException("Game is not in progress");
    }

    if (session.getState() == null) {
        throw new IllegalStateException("Game state is corrupted or missing");
    }

    if (!gameEngine.isMoveValid(session, move)) {
        throw new IllegalArgumentException("Invalid move");
    }

    // 1. Execute the move
    GameState newState = gameEngine.executeMove(session, move);
    session.setState(newState);
    session.setStatus(newState.getStatus());
    
    // 2. Save the immediate state
    GameSession savedSession = gameStateRepository.save(session);

    // 3. KAFKA PRODUCER: If the move ended the game, fire the event
    if (savedSession.getStatus() != GameStatus.IN_PROGRESS) {
        
        // Identify who won and who lost. 
        // The player who just made the valid move that ended the game is the winner.
        String winnerId = player.getUserId().toString(); // Or getUsername() if you prefer strings
        
        // Extract the opponent safely
        Player opponent = session.getPlayer1().getUserId().equals(player.getUserId()) 
                ? session.getPlayer2() 
                : session.getPlayer1();
                
        String loserId = opponent.getUserId().toString();

        // Create the event payload
        GameEndedEvent event = new GameEndedEvent(
            sessionId,
            winnerId,
            loserId,
            savedSession.getStatus().name() // e.g., "CHECKMATE", "DRAW", etc.
        );
        
        // Fire and forget to Kafka!
        gameEventPublisher.publishGameEnded(event);
    }

    return savedSession;
}
}
