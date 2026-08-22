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

                String requestedUsername = (principal != null && principal.getName() != null
                                && !principal.getName().isBlank())
                                                ? principal.getName()
                                                : moveRequest.getUsername();
                final String username = requestedUsername == null || requestedUsername.isBlank()
                                ? "Guest_Player"
                                : requestedUsername;

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
                        System.err.println("Failed to execute move for " + username + " ("
                                        + player.getUserId() + "): " + e.getMessage());
                        // Broadcast existing session state so clients sync back to valid board position
                        gameStateRepository.findById(sessionId).ifPresent(existingSession -> messagingTemplate
                                        .convertAndSend("/topic/game/" + sessionId, existingSession));
                }
        }

        @MessageMapping("/game/{sessionId}/state")
        public void sendCurrentState(@DestinationVariable UUID sessionId) {
                if (sessionId == null) {
                        return;
                }
                gameStateRepository.findById(sessionId).ifPresent(
                                session -> messagingTemplate.convertAndSend("/topic/game/" + sessionId, session));
        }

        @MessageMapping("/game/{sessionId}/resign")
        public void resignMatch(@DestinationVariable UUID sessionId,
                        @Payload(required = false) MoveRequest resignRequest,
                        Principal principal) {
                if (sessionId == null) return;
                String requestedUsername = (principal != null && principal.getName() != null && !principal.getName().isBlank())
                                ? principal.getName()
                                : (resignRequest != null ? resignRequest.getUsername() : "Guest_Player");
                Player player = Player.builder().username(requestedUsername).build();
                try {
                        GameSession session = gameService.resignGame(sessionId, player);
                        messagingTemplate.convertAndSend("/topic/game/" + sessionId, session);
                } catch (Exception e) {
                        System.err.println("Failed to process resignation: " + e.getMessage());
                }
        }

        @MessageMapping("/game/{sessionId}/draw")
        public void offerDraw(@DestinationVariable UUID sessionId,
                        @Payload(required = false) MoveRequest drawRequest,
                        Principal principal) {
                if (sessionId == null) return;
                String requestedUsername = (principal != null && principal.getName() != null && !principal.getName().isBlank())
                                ? principal.getName()
                                : (drawRequest != null ? drawRequest.getUsername() : "Guest_Player");
                Player player = Player.builder().username(requestedUsername).build();
                try {
                        GameSession session = gameService.drawGame(sessionId, player);
                        messagingTemplate.convertAndSend("/topic/game/" + sessionId, session);
                } catch (Exception e) {
                        System.err.println("Failed to process draw offer: " + e.getMessage());
                }
        }
}
