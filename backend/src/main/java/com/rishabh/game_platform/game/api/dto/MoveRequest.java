package com.rishabh.game_platform.game.api.dto;

import lombok.Data;

@Data
public class MoveRequest {
    private String from;
    private String to;
    private String promotion; // e.g., "Q" (optional, used if a pawn reaches the end)
    private String username; // Fallback identity when the STOMP principal is unavailable
}
