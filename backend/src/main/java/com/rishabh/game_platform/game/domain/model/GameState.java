package com.rishabh.game_platform.game.domain.model;

import com.rishabh.game_platform.game.domain.enums.GameStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameState {
    private String boardState; 
    private String currentTurn;
    private GameStatus status;
}