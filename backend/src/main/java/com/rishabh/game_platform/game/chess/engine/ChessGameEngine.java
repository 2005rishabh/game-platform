package com.rishabh.game_platform.game.chess.engine;

import org.springframework.stereotype.Component;

import com.github.bhlangonijr.chesslib.Board;
import com.rishabh.game_platform.game.domain.enums.GameStatus;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.GameState;
import com.rishabh.game_platform.game.domain.model.Move;
import com.rishabh.game_platform.game.engine.core.GameEngine;

@Component
public class ChessGameEngine implements GameEngine{

    @Override
    public GameState initializeGame() {
        Board board = new Board();

        return GameState.builder()
        .boardState(board.getFen())
        .currentTurn("WHITE")
        .status(GameStatus.IN_PROGRESS)
        .build();
    }

    @Override
    public boolean isMoveValid(GameSession session, Move move) {
        
    }

    @Override
    public GameState executeMove(GameSession session, Move move) {
        
    }

}
