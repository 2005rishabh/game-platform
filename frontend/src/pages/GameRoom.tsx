import { useState, useEffect, useRef, useMemo } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Chess, type Square } from "chess.js";
import { Chessboard } from "react-chessboard";
import { useWebSocket } from "../hooks/useWebSocket";
import {
  playMoveSound,
  playCaptureSound,
  playCheckSound,
  playGameOverSound,
} from "../utils/audio";

interface IncomingSession {
  gameId?: string;
  status?: string;
  player1?: { username: string };
  player2?: { username: string };
  state?: { boardState?: string };
}

const PIECE_VALUES: Record<string, number> = {
  p: 1,
  n: 3,
  b: 3,
  r: 5,
  q: 9,
};

const PIECE_SYMBOLS: Record<"w" | "b", Record<string, string>> = {
  w: { p: "♙", n: "♘", b: "♗", r: "♖", q: "♕" },
  b: { p: "♟", n: "♞", b: "♝", r: "♜", q: "♛" },
};

export default function GameRoom() {
  const { sessionId } = useParams();
  const navigate = useNavigate();

  const {
    isConnected,
    publishMove,
    requestGameState,
    subscribeToGame,
    stompClient,
  } = useWebSocket();

  // Initialize headless chess engine
  const [game, setGame] = useState(new Chess());

  // Game lifecycle & player roles
  const [playerColor, setPlayerColor] = useState<"white" | "black">("white");
  const [isMyTurn, setIsMyTurn] = useState<boolean>(false);
  const [hasReceivedState, setHasReceivedState] = useState<boolean>(false);
  const [isMovePending, setIsMovePending] = useState(false);
  const [gameStatus, setGameStatus] = useState<string>("IN_PROGRESS");

  // Player info
  const [player1Name, setPlayer1Name] = useState<string>("Player 1");
  const [player2Name, setPlayer2Name] = useState<string>("Player 2");

  // Chess Clocks (10-minute Rapid)
  const [whiteTime, setWhiteTime] = useState<number>(600);
  const [blackTime, setBlackTime] = useState<number>(600);

  // References for authoritative position tracking
  const gameRef = useRef(game);
  const pendingMoveRef = useRef(false);

  // Notation history
  const [moveHistory, setMoveHistory] = useState<string[]>([]);

  // Logged-in user
  const currentUsername = localStorage.getItem("username") || "Player";

  // Timers countdown effect
  useEffect(() => {
    if (!hasReceivedState || gameStatus !== "IN_PROGRESS") return;

    const timer = setInterval(() => {
      const activeColor = gameRef.current.turn();
      if (activeColor === "w") {
        setWhiteTime((prev) => Math.max(0, prev - 1));
      } else {
        setBlackTime((prev) => Math.max(0, prev - 1));
      }
    }, 1000);

    return () => clearInterval(timer);
  }, [hasReceivedState, gameStatus]);

  // STOMP WebSocket Subscription
  useEffect(() => {
    if (!isConnected || !sessionId || !stompClient || !subscribeToGame) return;

    console.log("Subscribing to game room channel: ", sessionId);

    const subscription = subscribeToGame(
      sessionId,
      (incomingSession: IncomingSession) => {
        console.log("Authoritative game state received from server!", incomingSession);

        setHasReceivedState(true);

        if (incomingSession?.status) {
          setGameStatus(incomingSession.status);
        }

        if (incomingSession?.player1?.username) {
          setPlayer1Name(incomingSession.player1.username);
        }
        if (incomingSession?.player2?.username) {
          setPlayer2Name(incomingSession.player2.username);
        }

        // 1. Determine Player Role
        let myRole: "white" | "black" = "white";
        if (incomingSession?.player2?.username === currentUsername) {
          myRole = "black";
        }
        setPlayerColor(myRole);

        // 2. Process Authoritative Board Position
        const boardState = incomingSession?.state?.boardState;
        if (!boardState) return;

        try {
          const nextGame = new Chess();
          nextGame.load(boardState);

          const previousGame = gameRef.current;
          if (previousGame.fen() !== nextGame.fen()) {
            const confirmedMove = previousGame
              .moves({ verbose: true })
              .map((candidate) => {
                const attempt = new Chess(previousGame.fen());
                const move = attempt.move({
                  from: candidate.from,
                  to: candidate.to,
                  promotion: candidate.promotion,
                });
                return attempt.fen() === nextGame.fen() ? move : null;
              })
              .find(Boolean);

            if (confirmedMove) {
              setMoveHistory((history) => [...history, confirmedMove.san]);

              // Play Audio Cue based on move outcome
              if (nextGame.isGameOver()) {
                playGameOverSound();
              } else if (nextGame.inCheck()) {
                playCheckSound();
              } else if (confirmedMove.captured) {
                playCaptureSound();
              } else {
                playMoveSound();
              }
            }
          }

          gameRef.current = nextGame;
          setGame(nextGame);
          setIsMovePending(false);
          pendingMoveRef.current = false;

          // Update Turn Status
          const activeTurnColor = nextGame.turn() === "w" ? "white" : "black";
          setIsMyTurn(activeTurnColor === myRole);
        } catch (e) {
          console.error("Failed to load authoritative FEN from server:", e);
        }
      }
    );

    requestGameState(sessionId);

    return () => {
      if (subscription) subscription.unsubscribe();
    };
  }, [
    isConnected,
    sessionId,
    requestGameState,
    stompClient,
    subscribeToGame,
    currentUsername,
  ]);

  // Find King square for check highlighting
  const kingCheckSquare = useMemo(() => {
    if (!game.inCheck()) return null;
    const turnColor = game.turn();
    const board = game.board();

    for (let r = 0; r < 8; r++) {
      for (let c = 0; c < 8; c++) {
        const piece = board[r][c];
        if (piece && piece.type === "k" && piece.color === turnColor) {
          return piece.square as Square;
        }
      }
    }
    return null;
  }, [game]);

  // Calculate Graveyard / Captured pieces & Material difference
  const capturedPieces = useMemo(() => {
    const initial: Record<string, number> = { p: 8, n: 2, b: 2, r: 2, q: 1 };
    const activeWhite: Record<string, number> = { p: 0, n: 0, b: 0, r: 0, q: 0 };
    const activeBlack: Record<string, number> = { p: 0, n: 0, b: 0, r: 0, q: 0 };

    game.board().forEach((row) => {
      row.forEach((piece) => {
        if (!piece || piece.type === "k") return;
        if (piece.color === "w") {
          activeWhite[piece.type] = (activeWhite[piece.type] || 0) + 1;
        } else {
          activeBlack[piece.type] = (activeBlack[piece.type] || 0) + 1;
        }
      });
    });

    const whiteCaptured: string[] = [];
    const blackCaptured: string[] = [];
    let whiteScore = 0;
    let blackScore = 0;

    Object.keys(initial).forEach((type) => {
      const missingWhite = Math.max(0, initial[type] - (activeWhite[type] || 0));
      const missingBlack = Math.max(0, initial[type] - (activeBlack[type] || 0));

      for (let i = 0; i < missingWhite; i++) whiteCaptured.push(type);
      for (let i = 0; i < missingBlack; i++) blackCaptured.push(type);

      whiteScore += (activeWhite[type] || 0) * PIECE_VALUES[type];
      blackScore += (activeBlack[type] || 0) * PIECE_VALUES[type];
    });

    return {
      whiteCaptured, // White pieces captured by Black
      blackCaptured, // Black pieces captured by White
      materialDiff: whiteScore - blackScore, // + for White advantage, - for Black advantage
    };
  }, [game]);

  // Handle piece drop on board
  function onDrop(
    sourceSquareOrObj: string | { sourceSquare: string; targetSquare: string | null },
    targetSquareStr?: string | null
  ) {
    let sourceSquare: string;
    let targetSquare: string | null;

    if (typeof sourceSquareOrObj === "string") {
      sourceSquare = sourceSquareOrObj;
      targetSquare = targetSquareStr ?? null;
    } else if (sourceSquareOrObj && typeof sourceSquareOrObj === "object") {
      sourceSquare = sourceSquareOrObj.sourceSquare;
      targetSquare = sourceSquareOrObj.targetSquare;
    } else {
      return false;
    }

    if (
      !targetSquare ||
      sourceSquare === targetSquare ||
      isMovePending ||
      pendingMoveRef.current ||
      (!isMyTurn && hasReceivedState) ||
      gameStatus !== "IN_PROGRESS"
    ) {
      if (sourceSquare !== targetSquare && !isMyTurn) {
        console.warn("Not your turn!");
      }
      return false;
    }

    console.log(`Attempting to move from ${sourceSquare} to ${targetSquare}`);

    try {
      const gameCopy = new Chess(gameRef.current.fen());
      const movingPiece = gameCopy.get(sourceSquare as any);
      const isPawn = movingPiece && movingPiece.type === "p";
      const isPromotionRank =
        (movingPiece?.color === "w" && targetSquare.endsWith("8")) ||
        (movingPiece?.color === "b" && targetSquare.endsWith("1"));
      const promotionChoice = isPawn && isPromotionRank ? "q" : undefined;

      let move;
      try {
        move = gameCopy.move({
          from: sourceSquare,
          to: targetSquare,
          promotion: promotionChoice,
        });
      } catch {
        console.warn(`Illegal or out-of-turn move: ${sourceSquare} to ${targetSquare}`);
        return false;
      }

      if (!move) {
        console.warn("chess.js rejected move as illegal.");
        return false;
      }

      if (sessionId && publishMove) {
        pendingMoveRef.current = true;
        setIsMovePending(true);
        publishMove(
          sessionId,
          sourceSquare,
          targetSquare,
          promotionChoice,
          currentUsername
        );
      } else {
        console.error("Cannot submit move: game session is unavailable.");
        return false;
      }

      return true;
    } catch (error) {
      console.error("Error in onDrop:", error);
      return false;
    }
  }

  // Resignation STOMP action
  const handleResign = () => {
    if (!stompClient || !sessionId) return;
    if (window.confirm("Are you sure you want to resign this match?")) {
      stompClient.publish({
        destination: `/app/game/${sessionId}/resign`,
        body: JSON.stringify({ username: currentUsername }),
      });
    }
  };

  // Draw offer STOMP action
  const handleOfferDraw = () => {
    if (!stompClient || !sessionId) return;
    stompClient.publish({
      destination: `/app/game/${sessionId}/draw`,
      body: JSON.stringify({ username: currentUsername }),
    });
  };

  // Square styling options (Red background for King in Check)
  const customSquareStyles: Record<string, React.CSSProperties> = {};
  if (kingCheckSquare) {
    customSquareStyles[kingCheckSquare] = {
      backgroundColor: "rgba(239, 68, 68, 0.85)",
      boxShadow: "inset 0 0 10px rgba(0,0,0,0.5)",
      borderRadius: "4px",
    };
  }

  const boardOptions = {
    position: game.fen(),
    onPieceDrop: onDrop,
    boardOrientation: playerColor,
    allowDragging: !isMovePending && isMyTurn && gameStatus === "IN_PROGRESS",
    squareStyles: customSquareStyles,
    boardStyle: { touchAction: "none" },
    darkSquareStyle: { backgroundColor: "#475569" },
    lightSquareStyle: { backgroundColor: "#94A3B8" },
    animationDurationInMs: 300,
  };

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs < 10 ? "0" : ""}${secs}`;
  };

  const movePairs = [];
  for (let i = 0; i < moveHistory.length; i += 2) {
    movePairs.push({
      turn: Math.floor(i / 2) + 1,
      white: moveHistory[i],
      black: moveHistory[i + 1] || "",
    });
  }

  const isGameOver = gameStatus !== "IN_PROGRESS" || game.isGameOver();

  return (
    <div className="flex flex-col items-center justify-center min-h-screen p-4 bg-[var(--color-midnight)] relative">
      {/* Top Bar: Match Info & Timers */}
      <div className="w-full max-w-4xl flex flex-wrap justify-between items-center mb-6 px-6 py-4 bg-[var(--color-charcoal)] border border-gray-800 rounded-sm shadow-xl gap-4">
        <div>
          <h2 className="text-xl font-serif text-white tracking-widest uppercase">
            LIVE GRANDMASTER MATCH
          </h2>
          <p className="text-xs text-gray-500 font-mono tracking-wider">
            ID: {sessionId} | Role:{" "}
            <span className="text-[var(--color-premium-gold)] uppercase font-bold">
              {playerColor}
            </span>
          </p>
        </div>

        <div className="flex items-center gap-6">
          {/* White Clock */}
          <div className="flex flex-col items-center px-4 py-2 bg-[var(--color-midnight)] border border-gray-800 rounded">
            <span className="text-[10px] text-gray-400 font-mono tracking-wider uppercase">
              White ({player1Name})
            </span>
            <span className={`text-lg font-mono font-bold ${game.turn() === "w" ? "text-green-400 animate-pulse" : "text-gray-300"}`}>
              {formatTime(whiteTime)}
            </span>
          </div>

          {/* Black Clock */}
          <div className="flex flex-col items-center px-4 py-2 bg-[var(--color-midnight)] border border-gray-800 rounded">
            <span className="text-[10px] text-gray-400 font-mono tracking-wider uppercase">
              Black ({player2Name})
            </span>
            <span className={`text-lg font-mono font-bold ${game.turn() === "b" ? "text-green-400 animate-pulse" : "text-gray-300"}`}>
              {formatTime(blackTime)}
            </span>
          </div>
        </div>

        <div className="flex flex-col items-end">
          <span className="text-xs tracking-[0.2em] text-gray-400 uppercase mb-1">
            Turn Indicator
          </span>
          <span className={`text-sm font-bold tracking-widest ${isMyTurn ? "text-green-400 animate-pulse" : "text-yellow-500"}`}>
            {isGameOver ? "GAME OVER" : isMyTurn ? "YOUR TURN" : "OPPONENT'S TURN"}
          </span>
        </div>
      </div>

      {/* Arena Layout */}
      <div className="w-full max-w-4xl flex flex-col lg:flex-row gap-8 items-center justify-center">
        {/* Board & Player Graveyards */}
        <div className="w-full max-w-[500px] flex flex-col gap-2">
          {/* Opponent Profile & Captured Pieces */}
          <div className="flex justify-between items-center bg-[var(--color-charcoal)] px-4 py-2 border border-gray-800 rounded-t-sm text-xs font-mono">
            <span className="text-gray-300 font-bold">
              👤 {playerColor === "white" ? player2Name : player1Name}
            </span>
            <div className="flex items-center gap-1 text-gray-400 text-sm">
              {(playerColor === "white" ? capturedPieces.whiteCaptured : capturedPieces.blackCaptured).map((p, idx) => (
                <span key={idx}>{PIECE_SYMBOLS[playerColor === "white" ? "w" : "b"][p]}</span>
              ))}
              {playerColor === "white" && capturedPieces.materialDiff < 0 && (
                <span className="text-green-400 font-bold ml-1">+{Math.abs(capturedPieces.materialDiff)}</span>
              )}
              {playerColor === "black" && capturedPieces.materialDiff > 0 && (
                <span className="text-green-400 font-bold ml-1">+{capturedPieces.materialDiff}</span>
              )}
            </div>
          </div>

          {/* Main Chessboard */}
          <div className="w-full aspect-square shadow-[0_0_40px_rgba(0,0,0,0.8)] border border-gray-800 rounded-sm overflow-hidden p-2 bg-[var(--color-charcoal)] relative">
            <Chessboard options={boardOptions} />
          </div>

          {/* Local Player Profile & Captured Pieces */}
          <div className="flex justify-between items-center bg-[var(--color-charcoal)] px-4 py-2 border border-gray-800 rounded-b-sm text-xs font-mono">
            <span className="text-[var(--color-premium-gold)] font-bold">
              👤 {currentUsername} (YOU)
            </span>
            <div className="flex items-center gap-1 text-gray-400 text-sm">
              {(playerColor === "white" ? capturedPieces.blackCaptured : capturedPieces.whiteCaptured).map((p, idx) => (
                <span key={idx}>{PIECE_SYMBOLS[playerColor === "white" ? "b" : "w"][p]}</span>
              ))}
              {playerColor === "white" && capturedPieces.materialDiff > 0 && (
                <span className="text-green-400 font-bold ml-1">+{capturedPieces.materialDiff}</span>
              )}
              {playerColor === "black" && capturedPieces.materialDiff < 0 && (
                <span className="text-green-400 font-bold ml-1">+{Math.abs(capturedPieces.materialDiff)}</span>
              )}
            </div>
          </div>
        </div>

        {/* Sidebar: Move History & Actions */}
        <div className="w-full lg:w-72 h-[560px] bg-[var(--color-charcoal)] border border-gray-800 rounded-sm p-4 flex flex-col justify-between shadow-xl">
          <div>
            <h3 className="text-sm font-serif text-[var(--color-premium-gold)] tracking-[0.2em] uppercase mb-4 border-b border-gray-700 pb-2">
              Live Notation
            </h3>
            <div className="h-[360px] overflow-y-auto font-mono text-sm space-y-1 pr-2">
              {movePairs.length === 0 ? (
                <p className="text-gray-500 italic text-xs text-center mt-4">
                  Waiting for first move...
                </p>
              ) : (
                movePairs.map((pair) => (
                  <div
                    key={pair.turn}
                    className="flex justify-between py-1 px-2 rounded hover:bg-gray-700/50 transition-colors"
                  >
                    <span className="text-gray-500 w-8">{pair.turn}.</span>
                    <span className="text-gray-200 flex-1">{pair.white}</span>
                    <span className="text-gray-400 flex-1">{pair.black}</span>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex gap-2 pt-4 border-t border-gray-800">
            <button
              onClick={handleOfferDraw}
              disabled={isGameOver}
              className="flex-1 py-2 bg-gray-800 hover:bg-gray-700 text-xs font-bold tracking-wider uppercase rounded transition-colors disabled:opacity-50"
            >
              Offer Draw
            </button>
            <button
              onClick={handleResign}
              disabled={isGameOver}
              className="flex-1 py-2 bg-red-900/60 hover:bg-red-800 text-red-200 border border-red-800 text-xs font-bold tracking-wider uppercase rounded transition-colors disabled:opacity-50"
            >
              Resign
            </button>
          </div>
        </div>
      </div>

      {/* Game Over Modal Overlay */}
      {isGameOver && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-[var(--color-charcoal)] border-2 border-[var(--color-premium-gold)] max-w-md w-full p-8 rounded-sm shadow-2xl text-center flex flex-col items-center space-y-6 animate-fade-in">
            <div className="w-16 h-16 rounded-full bg-[var(--color-premium-gold)]/10 border border-[var(--color-premium-gold)] flex items-center justify-center text-3xl">
              🏆
            </div>

            <div>
              <h2 className="text-3xl font-serif text-white tracking-widest uppercase mb-2">
                MATCH CONCLUDED
              </h2>
              <p className="text-lg font-bold text-[var(--color-premium-gold)] uppercase tracking-wider">
                Status: {gameStatus}
              </p>
              <p className="text-xs text-gray-400 mt-2 font-mono">
                Authoritative ELO rating updates processed via Kafka stream.
              </p>
            </div>

            <button
              onClick={() => navigate("/")}
              className="w-full py-3 bg-gradient-to-r from-[var(--color-premium-gold)] to-[#AA8C2C] text-[var(--color-charcoal)] font-bold uppercase tracking-[0.2em] rounded-sm hover:opacity-90 transition-opacity"
            >
              Return to Dashboard
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
