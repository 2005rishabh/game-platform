package com.rishabh.game_platform.matchmaking.api.websocket;

import java.security.Principal;
import java.util.Map;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import com.rishabh.game_platform.matchmaking.application.service.MatchmakingService;

@Controller
public class MatchmakingWebSocketController {

    private final MatchmakingService matchmakingService;

    // Spring automatically injects the service here
    public MatchmakingWebSocketController(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    @MessageMapping("/matchmaking.join")
    public void joinMatchmaking(Map<String, Object> payload, Principal principal) {
        String playerId = null;
        if (payload != null && payload.get("playerId") instanceof String idStr) {
            playerId = idStr;
        } else if (principal != null && principal.getName() != null) {
            playerId = principal.getName();
        }

        if (playerId != null && !playerId.isBlank()) {
            matchmakingService.processJoinRequest(playerId);
        }
    }
}