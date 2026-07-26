package com.rishabh.game_platform.game.domain.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

import com.rishabh.game_platform.game.domain.enums.GameStatus;
import com.rishabh.game_platform.game.domain.enums.GameType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameSession {
    private UUID gameId;
    private GameType gameType;
    private GameStatus status;
    private Player whitePlayer;
    private Player blackPlayer;
    
    // We will define GameState next when we build the Engine
    // private GameState state; 
    
    private Instant createdAt;
}