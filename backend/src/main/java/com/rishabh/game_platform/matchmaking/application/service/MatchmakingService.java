package com.rishabh.game_platform.matchmaking.application.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
import com.rishabh.game_platform.matchmaking.domain.ports.MatchmakingQueue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchmakingService {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameEngine gameEngine;
    private final GameStateRepository gameStateRepository;

    // Spring Boot injects your new Redis queue here instead of the old memory
    // queue!
    private final MatchmakingQueue matchmakingQueue;

    public void processJoinRequest(Player player, GameType gameType) {
        if (player.getUserId() == null) {
            return;
        }

        log.info("Player joining matchmaking queue: {}", player.getUsername());

        // Look for someone who is already waiting before adding this player.
        // Adding first causes the queue implementation to pop the current player
        // as a self-match, leaving nobody available for the next request.
        Optional<Player> opponentOpt = matchmakingQueue.extractOpponent(gameType, player);

        if (opponentOpt.isPresent()) {
            Player opponent = opponentOpt.get();

            UUID gameId = UUID.randomUUID();
            String sessionId = gameId.toString();

            player.setColor(PlayerColor.WHITE);
            opponent.setColor(PlayerColor.BLACK);

            GameState initialState = gameEngine.initializeGame();

            GameSession newSession = GameSession.builder()
                    .gameId(gameId)
                    .gameType(gameType)
                    .status(GameStatus.IN_PROGRESS)
                    .player1(player)
                    .player2(opponent)
                    .state(initialState)
                    .build();

            gameStateRepository.save(newSession);

            log.info("Match found in Redis! Creating GameRoom: {}", sessionId);

            Map<String, String> matchData = Map.of("sessionId", sessionId);

            // Broadcast the Session ID back to both players' specific private topics
            // Assuming your frontend subscribes to /topic/match/{playerId} or {username}
            messagingTemplate.convertAndSend("/topic/match/" + player.getUsername(), matchData);
            messagingTemplate.convertAndSend("/topic/match/" + opponent.getUsername(), matchData);
        } else {
            // No opponent is waiting, so this player becomes the waiting player.
            matchmakingQueue.addPlayer(player, gameType);
            log.info("No opponent available; queued player {}", player.getUsername());
        }
    }

    public void processCancelRequest(java.util.UUID playerId) {
        log.info("Processing cancel request for player UUID: {}", playerId);
        matchmakingQueue.removePlayer(playerId);
    }
}