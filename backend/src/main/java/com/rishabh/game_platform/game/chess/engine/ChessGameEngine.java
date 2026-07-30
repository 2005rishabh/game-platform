package com.rishabh.game_platform.game.chess.engine;

import org.springframework.stereotype.Component;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.MoveList;
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
        Board board = new Board();
        board.loadFromFen(session.getState().getBoardState());

        com.github.bhlangonijr.chesslib.move.Move chessMove = convertToChesslibMove(move, board);

        var legalMoves = board.legalMoves();

        return legalMoves.contains(chessMove);

    }

    @Override
    public GameState executeMove(GameSession session, Move move) {
        Board board = new Board();
        board.loadFromFen(session.getState().getBoardState());

        com.github.bhlangonijr.chesslib.move.Move chessMove = convertToChesslibMove(move, board);

        board.doMove(chessMove);
        
        GameStatus newStatus = GameStatus.IN_PROGRESS;

        if (board.isMated()) {
            newStatus = board.getSideToMove().name().equals("WHITE") ? GameStatus.BLACK_WON : GameStatus.WHITE_WON;
        } else if (board.isDraw() || board.isStaleMate()) {
            newStatus = GameStatus.DRAW;
        }


        return GameState.builder()
        .boardState(board.getFen())
        .currentTurn(board.getSideToMove().name())
        .status(newStatus)
        .build();
    }

    /**
     * Helper method to map our pure Domain Move object into the 3rd-party library's Move object
     */
    private com.github.bhlangonijr.chesslib.move.Move convertToChesslibMove(Move move, Board board) {
        // Converts strings like "e2" to Square.E2
        Square fromSquare = Square.valueOf(move.getFrom().toUpperCase());
        Square toSquare = Square.valueOf(move.getTo().toUpperCase());
        
        // Handle pawn promotions (e.g. promoting a pawn to a Queen)
        if (move.getPromotion() != null && !move.getPromotion().isEmpty()) {
            String side = board.getSideToMove().name();
            // e.g., Creates "WHITE_QUEEN"
            Piece promotionPiece = Piece.valueOf(side + "_" + move.getPromotion().toUpperCase()); 
            return new com.github.bhlangonijr.chesslib.move.Move(fromSquare, toSquare, promotionPiece);
        }
        
        return new com.github.bhlangonijr.chesslib.move.Move(fromSquare, toSquare);
    }
}
