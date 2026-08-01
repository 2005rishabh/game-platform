package com.rishabh.game_platform.matchmaking.api.websocket;

import java.security.Principal;
import java.util.Optional;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.rishabh.game_platform.auth.infrastructure.persistence.UserEntity;
import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.matchmaking.application.service.MatchmakingService;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class MatchmakingWebSocketController {
    private final MatchmakingService matchmakingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    // A simple DTO to catch the incoming request(:>)
    @Data
    public static class MatchmakingRequest {
        private GameType gameType;
    }

    @MessageMapping("/matchmaking/join")
    public void joinQueue(@Payload MatchmakingRequest request, Principal principal) {
        // 1. Authenticate the user from the JWT principal
        UserEntity user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Player player = Player.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .eloRating(user.getEloRating() != null ? user.getEloRating() : 1200)
                .build();

        // 2. Throw them into the concurrent queue
        Optional<GameSession> match = matchmakingService.joinMatchmaking(player, request.getGameType());

        if (match.isPresent()) {
            GameSession session = match.get();

            // 3. Match found! Broadcast the GameSession to BOTH players' private channels
            messagingTemplate.convertAndSend("/topic/matchmaking/" + session.getPlayer1().getUserId(), session);
            messagingTemplate.convertAndSend("/topic/matchmaking/" + session.getPlayer2().getUserId(), session);
        } else {
            // 4. No match yet. Tell the current player's frontend to display a loading
            // spinner
            messagingTemplate.convertAndSend("/topic/matchmaking/" + player.getUserId(), "WAITING");
        }
    }
}
