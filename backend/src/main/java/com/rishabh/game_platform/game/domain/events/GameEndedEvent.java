package com.rishabh.game_platform.game.domain.events;

import java.util.UUID;

public record GameEndedEvent(
        UUID gameId,
        String winnerId,
        String loserId,
        String reason // e.g., "CHECKMATE", "RESIGNATION", "TIMEOUT"
) {
}