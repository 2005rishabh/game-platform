package com.rishabh.game_platform.matchmaking.api.websocket;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.rishabh.game_platform.matchmaking.application.service.MatchmakingService;
import com.rishabh.game_platform.shared.util.IdUtils;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.model.Player;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MatchmakingWebSocketController {

    private final MatchmakingService matchmakingService;

    private UUID parseToUuid(String input) {
        if (input == null || input.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8));
        }
    }

    @MessageMapping("/matchmaking.join")
    public void joinMatchmaking(Map<String, Object> payload, Principal principal) {
        String rawPlayerId = payload.getOrDefault("playerId", "").toString();
        String username = payload.getOrDefault("username", "").toString();

        if (rawPlayerId.isBlank() && principal != null) {
            rawPlayerId = principal.getName();
            username = principal.getName();
        }

        // Safely converts "Guest_a1b2c3" or "rishabh" into a valid UUID
        Long playerId = IdUtils.toLongId(rawPlayerId);

        Player player = Player.builder()
                .userId(playerId) // Keeps your UUID / Long model intact
                .username(username.isBlank() ? rawPlayerId : username)
                .eloRating(1200)
                .build();

        log.info("Receiving join request for player UUID: {} (username: {})", playerId, player.getUsername());
        matchmakingService.processJoinRequest(player, GameType.CHESS);
    }

    @MessageMapping("/matchmaking.cancel")
    public void cancelMatchmaking(Map<String, Object> payload, Principal principal) {
        String rawPlayerId = payload.getOrDefault("playerId", "").toString();

        if (rawPlayerId.isBlank() && principal != null) {
            rawPlayerId = principal.getName();
        }

        UUID playerId = parseToUuid(rawPlayerId);
        log.info("User UUID {} is aborting matchmaking.", playerId);
        
        matchmakingService.processCancelRequest(playerId);
        // Matches signature: void processCancelRequest(UUID playerId)
    }
}