package com.rishabh.game_platform.game.api.dto;

import com.rishabh.game_platform.game.domain.enums.GameType;
import lombok.Data;

@Data
public class CreateGameRequest {
    private GameType gameType;
}