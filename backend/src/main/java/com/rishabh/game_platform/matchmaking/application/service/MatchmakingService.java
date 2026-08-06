package com.rishabh.game_platform.matchmaking.application.service;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.rishabh.game_platform.game.domain.enums.GameStatus;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.enums.PlayerColor;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.GameState;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.game.domain.ports.GameStateRepository;
import com.rishabh.game_platform.game.engine.core.GameEngine;

@Service
public class MatchmakingService {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameEngine gameEngine;
    private final GameStateRepository gameStateRepository;
    
    // Thread-safe queue for players waiting to play
    private final Queue<String> waitingPlayers = new ConcurrentLinkedQueue<>();

    public MatchmakingService(SimpMessagingTemplate messagingTemplate,
                              GameEngine gameEngine,
                              GameStateRepository gameStateRepository) {
        this.messagingTemplate = messagingTemplate;
        this.gameEngine = gameEngine;
        this.gameStateRepository = gameStateRepository;
    }

    public void processJoinRequest(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return;
        }

        System.out.println("Player joined queue: " + playerId);

        // Add to queue if they aren't already waiting
        if (!waitingPlayers.contains(playerId)) {
            waitingPlayers.add(playerId);
        }

        // Matchmaking logic: 2 players found
        if (waitingPlayers.size() >= 2) {
            String player1Id = waitingPlayers.poll();
            String player2Id = waitingPlayers.poll();

            UUID gameId = UUID.randomUUID();
            String sessionId = gameId.toString();

            Player host = Player.builder()
                    .username(player1Id)
                    .color(PlayerColor.WHITE)
                    .eloRating(1200)
                    .build();

            Player guest = Player.builder()
                    .username(player2Id)
                    .color(PlayerColor.BLACK)
                    .eloRating(1200)
                    .build();

            GameState initialState = gameEngine.initializeGame();

            GameSession newSession = GameSession.builder()
                    .gameId(gameId)
                    .gameType(GameType.CHESS)
                    .status(GameStatus.IN_PROGRESS)
                    .player1(host)
                    .player2(guest)
                    .state(initialState)
                    .build();

            gameStateRepository.save(newSession);

            System.out.println("Match found! Creating GameRoom: " + sessionId);

            // Construct the payload expected by your React frontend
            Map<String, String> matchData = Map.of("sessionId", sessionId);

            // Broadcast the Session ID back to both players' specific private topics
            messagingTemplate.convertAndSend("/topic/match/" + player1Id, matchData);
            messagingTemplate.convertAndSend("/topic/match/" + player2Id, matchData);
        }
    }
}