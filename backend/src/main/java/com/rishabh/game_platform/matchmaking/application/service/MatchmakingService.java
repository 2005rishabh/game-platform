package com.rishabh.game_platform.matchmaking.application.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.rishabh.game_platform.game.application.service.GameService;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.matchmaking.domain.ports.MatchmakingQueue;

import lombok.RequiredArgsConstructor;

@Service
public class MatchmakingService {

    private final SimpMessagingTemplate messagingTemplate;
    
    // Thread-safe queue for players waiting to play
    private final Queue<String> waitingPlayers = new ConcurrentLinkedQueue<>();

    public MatchmakingService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void processJoinRequest(String playerId) {
        System.out.println("Player joined queue: " + playerId);

        // Add to queue if they aren't already waiting
        if (!waitingPlayers.contains(playerId)) {
            waitingPlayers.add(playerId);
        }

        // Matchmaking logic: 2 players found
        if (waitingPlayers.size() >= 2) {
            String player1 = waitingPlayers.poll();
            String player2 = waitingPlayers.poll();

            String sessionId = UUID.randomUUID().toString();
            System.out.println("Match found! Creating GameRoom: " + sessionId);

            // Construct the payload expected by your React frontend
            Map<String, String> matchData = Map.of("sessionId", sessionId);

            // Broadcast the Session ID back to both players' specific private topics
            messagingTemplate.convertAndSend("/topic/match/" + player1, matchData);
            messagingTemplate.convertAndSend("/topic/match/" + player2, matchData);
        }
    }
}