export type PieceColor = 'WHITE' | 'BLACK';
export type MatchStatus = 'WAITING' | 'IN_PROGRESS' | 'COMPLETED' | 'ABANDONED';

export interface Player {
  id: string; // UUID from the backend
  username: string;
  eloRating: number;
}

export interface GameState {
  sessionId: string; // The UUID routing the match
  whitePlayer: Player;
  blackPlayer: Player;
  currentTurn: PieceColor;
  status: MatchStatus;
  fen: string; // Standard Forsyth-Edwards Notation for board state
  moveHistory: string[]; // Standard Algebraic Notation (e.g., ["e4", "e5", "Nf3"])
}

export interface MovePayload {
  sessionId: string;
  playerId: string;
  fromSquare: string; // e.g., 'e2'
  toSquare: string;   // e.g., 'e4'
  promotion?: string; // e.g., 'q' for queen (optional)
}

export interface SocketMessage<T> {
  type: 'GAME_START' | 'MOVE_PLAYED' | 'ERROR' | 'OPPONENT_DISCONNECTED';
  payload: T;
  timestamp: string;
}