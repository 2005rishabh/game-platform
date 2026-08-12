package com.rishabh.game_platform.game.api.websocket;

import java.security.Principal;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import com.rishabh.game_platform.game.api.dto.MoveRequest;
import com.rishabh.game_platform.game.application.service.GameService;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.Move;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.shared.util.IdUtils;
import com.rishabh.game_platform.game.domain.ports.GameStateRepository;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

        private final GameService gameService;
        private final SimpMessagingTemplate messagingTemplate; // Spring's built-in message broadcaster
        private final UserRepository userRepository;
        private final GameStateRepository gameStateRepository;

        /**
         * Maps to the /app/game/{sessionId}/move endpoint defined in our
         * WebSocketConfig
         */
        @MessageMapping("/game/{sessionId}/move")
        public void executeMove(@DestinationVariable UUID sessionId,
                        @Payload MoveRequest moveRequest,
                        Principal principal) {

                if (sessionId == null || moveRequest == null || moveRequest.getFrom() == null
                                || moveRequest.getTo() == null) {
                        return;
                }

                String username = (principal != null && principal.getName() != null && !principal.getName().isBlank())
                                ? principal.getName()
                                : "Guest_Player";

                Player player = userRepository.findByUsername(username)
                                .map(user -> Player.builder()
                                                .userId(IdUtils.fromLong(user.getId()))
                                                .username(user.getUsername())
                                                .eloRating(user.getEloRating() != null ? user.getEloRating() : 1200)
                                                .build())
                                .orElseGet(() -> Player.builder()
                                                .username(username)
                                                .eloRating(1200)
                                                .build());

                Move move = Move.builder()
                                .from(moveRequest.getFrom())
                                .to(moveRequest.getTo())
                                .promotion(moveRequest.getPromotion())
                                .build();

                try {
                        GameSession updatedSession = gameService.executeMove(sessionId, player, move);
                        // Broadcast the authoritative session state back to all clients in this room
                        messagingTemplate.convertAndSend("/topic/game/" + sessionId, updatedSession);
                } catch (Exception e) {
                        System.err.println("Failed to execute move: " + e.getMessage());
                        // Broadcast existing session state so clients sync back to valid board position
                        gameStateRepository.findById(sessionId).ifPresent(existingSession -> messagingTemplate
                                        .convertAndSend("/topic/game/" + sessionId, existingSession));
                }
        }
}
