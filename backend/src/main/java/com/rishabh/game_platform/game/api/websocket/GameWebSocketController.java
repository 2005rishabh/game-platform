package com.rishabh.game_platform.game.api.websocket;

import java.security.Principal;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.rishabh.game_platform.auth.infrastructure.persistence.UserEntity;
import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import com.rishabh.game_platform.game.api.dto.MoveRequest;
import com.rishabh.game_platform.game.application.service.GameService;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.Move;
import com.rishabh.game_platform.game.domain.model.Player;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

        private final GameService gameService;
        private final SimpMessagingTemplate messagingTemplate; // Spring's built-in message broadcaster
        private final UserRepository userRepository;

        /**
         * Maps to the /app/game/{sessionId}/move endpoint defined in our
         * WebSocketConfig
         */
        @MessageMapping("/game/{sessionId}/move")
        public void executeMove(@DestinationVariable UUID sessionId,
                        @Payload MoveRequest moveRequest,
                        Principal principal) {

                if (principal == null) {
                        throw new IllegalStateException("Authenticated principal is required for game moves");
                }

                String username = principal.getName();

                UserEntity user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new RuntimeException("User not found in DB: " + username));

                Player player = Player.builder()
                                .userId(user.getId())
                                .username(user.getUsername())
                                .eloRating(user.getEloRating() != null ? user.getEloRating() : 1200)
                                .build();

                Move move = Move.builder()
                                .from(moveRequest.getFrom())
                                .to(moveRequest.getTo())
                                .promotion(moveRequest.getPromotion())
                                .build();

                GameSession updatedSession = gameService.executeMove(sessionId, player, move);

                // Broadcast the authoritative session state back to all clients in this room
                messagingTemplate.convertAndSend("/topic/game/" + sessionId, updatedSession);
        }
}
