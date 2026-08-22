package com.rishabh.game_platform.game.application.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final ConcurrentHashMap<UUID, Object> gameLocks = new ConcurrentHashMap<>();

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

        Object gameLock = gameLocks.computeIfAbsent(sessionId, ignored -> new Object());
        synchronized (gameLock) {
            GameSession session = gameStateRepository.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Game session not found"));

            if (session.getStatus() != GameStatus.IN_PROGRESS) {
                throw new IllegalStateException("Game is not in progress");
            }

            if (session.getState() == null) {
                throw new IllegalStateException("Game state is corrupted or missing");
            }

            if (!isPlayerTurn(session, player)) {
                throw new IllegalStateException("It is not this player's turn");
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
                String winnerId = getPlayerIdentifier(player);
                Player opponent = isSamePlayer(session.getPlayer1(), player)
                        ? session.getPlayer2()
                        : session.getPlayer1();

                String loserId = getPlayerIdentifier(opponent);

                // Create the event payload
                GameEndedEvent event = new GameEndedEvent(
                        sessionId,
                        winnerId,
                        loserId,
                        savedSession.getStatus().name() // e.g., "CHECKMATE", "DRAW", etc.
                );

                // Fire to Kafka!
                gameEventPublisher.publishGameEnded(event);
            }

            return savedSession;
        }
    }

    public GameSession resignGame(UUID sessionId, Player player) {
        if (sessionId == null || player == null) {
            throw new IllegalArgumentException("SessionId and player must not be null");
        }

        Object gameLock = gameLocks.computeIfAbsent(sessionId, ignored -> new Object());
        synchronized (gameLock) {
            GameSession session = gameStateRepository.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Game session not found"));

            if (session.getStatus() != GameStatus.IN_PROGRESS) {
                return session;
            }

            boolean isPlayer1Resigning = isSamePlayer(session.getPlayer1(), player);
            Player winner = isPlayer1Resigning ? session.getPlayer2() : session.getPlayer1();
            Player loser = isPlayer1Resigning ? session.getPlayer1() : session.getPlayer2();

            GameStatus finalStatus = isPlayer1Resigning ? GameStatus.BLACK_WON : GameStatus.WHITE_WON;
            session.setStatus(finalStatus);

            GameSession savedSession = gameStateRepository.save(session);

            GameEndedEvent event = new GameEndedEvent(
                    sessionId,
                    getPlayerIdentifier(winner),
                    getPlayerIdentifier(loser),
                    "RESIGNATION"
            );
            gameEventPublisher.publishGameEnded(event);

            return savedSession;
        }
    }

    public GameSession drawGame(UUID sessionId, Player player) {
        if (sessionId == null || player == null) {
            throw new IllegalArgumentException("SessionId and player must not be null");
        }

        Object gameLock = gameLocks.computeIfAbsent(sessionId, ignored -> new Object());
        synchronized (gameLock) {
            GameSession session = gameStateRepository.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Game session not found"));

            if (session.getStatus() != GameStatus.IN_PROGRESS) {
                return session;
            }

            session.setStatus(GameStatus.DRAW);
            GameSession savedSession = gameStateRepository.save(session);

            Player opponent = isSamePlayer(session.getPlayer1(), player)
                    ? session.getPlayer2()
                    : session.getPlayer1();

            GameEndedEvent event = new GameEndedEvent(
                    sessionId,
                    getPlayerIdentifier(player),
                    getPlayerIdentifier(opponent),
                    "DRAW"
            );
            gameEventPublisher.publishGameEnded(event);

            return savedSession;
        }
    }

    private boolean isPlayerTurn(GameSession session, Player player) {
        boolean isWhite = isSamePlayer(session.getPlayer1(), player);
        boolean isBlack = isSamePlayer(session.getPlayer2(), player);
        String turn = session.getState() != null ? session.getState().getCurrentTurn() : null;
        return (isWhite && "WHITE".equalsIgnoreCase(turn))
                || (isBlack && "BLACK".equalsIgnoreCase(turn));
    }

    private boolean isSamePlayer(Player p1, Player p2) {
        if (p1 == null || p2 == null) return false;
        if (p1.getUserId() != null && p2.getUserId() != null) {
            return p1.getUserId().equals(p2.getUserId());
        }
        if (p1.getUsername() != null && p2.getUsername() != null) {
            return p1.getUsername().equalsIgnoreCase(p2.getUsername());
        }
        return false;
    }

    private String getPlayerIdentifier(Player player) {
        if (player == null) return "Unknown";
        if (player.getUsername() != null && !player.getUsername().isBlank()) {
            return player.getUsername();
        }
        if (player.getUserId() != null) {
            return player.getUserId().toString();
        }
        return "Unknown";
    }
}
