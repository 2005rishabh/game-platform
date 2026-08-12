package com.rishabh.game_platform.game.domain.model;

import java.util.UUID;

import com.rishabh.game_platform.game.domain.enums.PlayerColor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class Player {
    private UUID userId;
    private String username;
    private PlayerColor color;
    private Integer eloRating;
}