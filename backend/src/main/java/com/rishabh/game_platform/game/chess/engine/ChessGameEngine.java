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
        if (session == null || session.getState() == null || session.getState().getBoardState() == null || move == null
                || move.getFrom() == null || move.getTo() == null) {
            return false;
        }

        Board board = new Board();
        board.loadFromFen(session.getState().getBoardState());

        com.github.bhlangonijr.chesslib.move.Move chessMove = convertToChesslibMove(move, board);

        var legalMoves = board.legalMoves();

        return legalMoves.contains(chessMove);
    }

    @Override
    public GameState executeMove(GameSession session, Move move) {
        if (session == null || session.getState() == null || session.getState().getBoardState() == null || move == null) {
            throw new IllegalArgumentException("Game session and move cannot be null");
        }

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
        Square fromSquare = Square.valueOf(move.getFrom().trim().toUpperCase());
        Square toSquare = Square.valueOf(move.getTo().trim().toUpperCase());

        Piece movingPiece = board.getPiece(fromSquare);
        boolean isPawn = movingPiece == Piece.WHITE_PAWN || movingPiece == Piece.BLACK_PAWN;
        boolean isPromotionRank = (movingPiece == Piece.WHITE_PAWN && toSquare.name().endsWith("8"))
                || (movingPiece == Piece.BLACK_PAWN && toSquare.name().endsWith("1"));

        if (isPawn && isPromotionRank && move.getPromotion() != null && !move.getPromotion().isBlank()) {
            String side = board.getSideToMove().name();
            String promoCode = move.getPromotion().trim().toUpperCase();
            String pieceName = switch (promoCode) {
                case "Q", "QUEEN" -> "QUEEN";
                case "R", "ROOK" -> "ROOK";
                case "B", "BISHOP" -> "BISHOP";
                case "N", "KNIGHT" -> "KNIGHT";
                default -> "QUEEN";
            };
            Piece promotionPiece = Piece.valueOf(side + "_" + pieceName);
            return new com.github.bhlangonijr.chesslib.move.Move(fromSquare, toSquare, promotionPiece);
        }

        return new com.github.bhlangonijr.chesslib.move.Move(fromSquare, toSquare);
    }
}
