package com.rishabh.game_platform.game.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Move {
    private String from;
    private String to;
    private String promotion; // Optional: e.g., "Q" for Queen in chess
    private Instant playedAt;
}