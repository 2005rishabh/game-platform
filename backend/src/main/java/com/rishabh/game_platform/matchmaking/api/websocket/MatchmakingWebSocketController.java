package com.rishabh.game_platform.matchmaking.api.websocket;

import com.rishabh.game_platform.matchmaking.application.service.MatchmakingService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class MatchmakingWebSocketController {

    private final MatchmakingService matchmakingService;

    // Spring automatically injects the service here
    public MatchmakingWebSocketController(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    @MessageMapping("/matchmaking.join")
    public void joinMatchmaking(Map<String, Object> payload) {
        // Extract the ID sent from React
        String playerId = (String) payload.get("playerId");
        
        // Delegate to the application layer
        matchmakingService.processJoinRequest(playerId);
    }
}