package com.rishabh.game_platform.game.engine.core;

import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.GameState;
import com.rishabh.game_platform.game.domain.model.Move;

public interface GameEngine {

    // it will help in creating a board
    GameState initializeGame();

    // check move done by player is valid or not
    boolean isMoveValid(GameSession session, Move move);

    // it will execute the game and return new game state
    GameState executeMove(GameSession session, Move move);
}