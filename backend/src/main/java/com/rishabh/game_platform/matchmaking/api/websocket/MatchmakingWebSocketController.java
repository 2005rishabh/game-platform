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
import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import com.rishabh.game_platform.shared.util.IdUtils;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.model.Player;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MatchmakingWebSocketController {

    private final MatchmakingService matchmakingService;
    private final UserRepository userRepository;

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

        // Use the same UUID representation that GameWebSocketController uses for
        // authenticated moves. Using a username-derived UUID here made every
        // server-side turn check fail for registered users.
        String fallbackPlayerId = rawPlayerId;
        java.util.UUID playerId = userRepository.findByUsername(username)
                .map(user -> IdUtils.fromLong(user.getId()))
                .orElseGet(() -> IdUtils.toUuid(fallbackPlayerId));

        Player player = Player.builder()
                .userId(playerId)
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

        String fallbackPlayerId = rawPlayerId;
        UUID playerId = userRepository.findByUsername(
                principal != null && principal.getName() != null && !principal.getName().isBlank()
                    ? principal.getName()
                    : rawPlayerId)
            .map(user -> IdUtils.fromLong(user.getId()))
                .orElseGet(() -> parseToUuid(fallbackPlayerId));
        log.info("User UUID {} is aborting matchmaking.", playerId);

        matchmakingService.processCancelRequest(playerId);
        // Matches signature: void processCancelRequest(UUID playerId)
    }
}