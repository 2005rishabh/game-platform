import { useState, useEffect, useRef } from "react";
import { useParams } from "react-router-dom";
import { Chess } from "chess.js";
import { Chessboard } from "react-chessboard";
import { useWebSocket } from "../hooks/useWebSocket";

interface IncomingSession {
  player1?: { username: string };
  player2?: { username: string };
  state?: { boardState?: string };
}

export default function GameRoom() {
  const { sessionId } = useParams();

  const { isConnected, publishMove, subscribeToGame, stompClient } =
    useWebSocket();

  // Initialize the headless chess engine
  const [game, setGame] = useState(new Chess());

  // State for player color, turn enforcement, and state tracking
  const [playerColor, setPlayerColor] = useState<"white" | "black">("white");
  const [isMyTurn, setIsMyTurn] = useState<boolean>(false);
  const [hasReceivedState, setHasReceivedState] = useState<boolean>(false);
  const [isMovePending, setIsMovePending] = useState(false);

  // The server is authoritative. Keep a ref so websocket callbacks and drops
  // always work from the latest confirmed position instead of a stale render.
  const gameRef = useRef(game);
  const pendingMoveRef = useRef(false);

  // NEW: State to store move history so FEN reloads don't wipe notation
  const [moveHistory, setMoveHistory] = useState<string[]>([]);

  // Grab the logged-in username to determine if they are Player 1 (White) or Player 2 (Black)
  const currentUsername = localStorage.getItem("username") || "rishabh";

  useEffect(() => {
    if (!isConnected || !sessionId || !stompClient || !subscribeToGame) return;

    console.log("Subscribing to game room channel: ", sessionId);

    const subscription = subscribeToGame(
      sessionId,
      (incomingSession: IncomingSession) => {
        console.log(
          "Authoritative game state received from server!",
          incomingSession,
        );

        setHasReceivedState(true);

        // 1. Determine Player Role (White or Black)
        let myRole: "white" | "black" = "white";
        if (incomingSession?.player2?.username === currentUsername) {
          myRole = "black";
        }
        setPlayerColor(myRole);

        // 2. Process the Authoritative Board State
        const boardState = incomingSession?.state?.boardState;

        if (!boardState) return;

        try {
          const nextGame = new Chess();
          nextGame.load(boardState);

          // Do not append notation optimistically. A move belongs in the
          // history only after the backend broadcasts the resulting position.
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
            }
          }

          gameRef.current = nextGame;
          setGame(nextGame);
          setIsMovePending(false);
          pendingMoveRef.current = false;

          // 3. Update Turn Status from the confirmed state.
          const activeTurnColor = nextGame.turn() === "w" ? "white" : "black";
          setIsMyTurn(activeTurnColor === myRole);
        } catch (e) {
          console.error("Failed to load authoritative FEN from server:", e);
        }
      },
    );

    return () => {
      if (subscription) subscription.unsubscribe();
    };
  }, [isConnected, sessionId, stompClient, subscribeToGame, currentUsername]);

  function onDrop({
    sourceSquare,
    targetSquare,
  }: {
    sourceSquare: string;
    targetSquare: string | null;
  }) {
    if (
      !targetSquare ||
      isMovePending ||
      pendingMoveRef.current ||
      (!isMyTurn && hasReceivedState)
    ) {
      console.warn("Not your turn!");
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
        console.warn(
          `Illegal or out-of-turn move: ${sourceSquare} to ${targetSquare}`,
        );
        return false;
      }

      if (!move) {
        console.warn("chess.js rejected the move as illegal.");
        return false;
      }

      if (sessionId && publishMove) {
        // Do not mutate the board locally. The next websocket message is the
        // only commit, preventing local state from racing server state.
        pendingMoveRef.current = true;
        setIsMovePending(true);
        publishMove(sessionId, sourceSquare, targetSquare, promotionChoice);
        console.log("Move submitted; waiting for authoritative server state.");
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

  const boardOptions = {
    position: game.fen(),
    onPieceDrop: onDrop,
    boardOrientation: playerColor,
    allowDragging: !isMovePending && (isMyTurn || !hasReceivedState),
    boardStyle: { touchAction: "none" },
    darkSquareStyle: { backgroundColor: "#475569" },
    lightSquareStyle: { backgroundColor: "#94A3B8" },
    animationDurationInMs: 300,
  };

  // Group our moveHistory state into standard pairs [Turn, White Move, Black Move]
  const movePairs = [];
  for (let i = 0; i < moveHistory.length; i += 2) {
    movePairs.push({
      turn: Math.floor(i / 2) + 1,
      white: moveHistory[i],
      black: moveHistory[i + 1] || "",
    });
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-screen p-4 bg-[var(--color-midnight)]">
      {/* Top Bar: Match Info */}
      <div className="w-full max-w-3xl flex justify-between items-center mb-8 px-6 py-4 bg-[var(--color-charcoal)] border border-gray-800 rounded-sm shadow-xl">
        <div>
          <h2 className="text-xl font-serif text-white tracking-widest uppercase">
            TESTING MATCH
          </h2>
          <p className="text-xs text-gray-500 font-mono tracking-wider">
            ID: {sessionId} | Playing as:{" "}
            <span className="text-[var(--color-premium-gold)] uppercase font-bold">
              {playerColor}
            </span>
          </p>
        </div>
        <div className="flex flex-col items-end">
          <span className="text-xs tracking-[0.2em] text-gray-400 uppercase mb-1">
            Match Status
          </span>
          <div className="flex items-center gap-2">
            <span
              className={`text-sm font-bold tracking-widest ${isMyTurn ? "text-green-400 animate-pulse" : "text-yellow-500"}`}
            >
              {isMyTurn ? "YOUR TURN" : "OPPONENT'S TURN"}
            </span>
          </div>
        </div>
      </div>

      {/* The Arena */}
      <div className="w-full max-w-3xl flex flex-col md:flex-row gap-8 items-center justify-center">
        {/* Chessboard Container */}
        <div className="w-full max-w-[500px] aspect-square shadow-[0_0_40px_rgba(0,0,0,0.8)] border border-gray-800 rounded-sm overflow-hidden p-2 bg-[var(--color-charcoal)]">
          <Chessboard options={boardOptions} />
        </div>

        {/* Move History Sidebar */}
        <div className="w-full md:w-64 h-[500px] bg-[var(--color-charcoal)] border border-gray-800 rounded-sm p-4 flex flex-col shadow-xl">
          <h3 className="text-sm font-serif text-[var(--color-premium-gold)] tracking-[0.2em] uppercase mb-4 border-b border-gray-700 pb-2">
            Live Notation
          </h3>
          <div className="flex-1 overflow-y-auto font-mono text-sm space-y-1 pr-2">
            {movePairs.length === 0 ? (
              <p className="text-gray-500 italic text-xs text-center mt-4">
                Waiting for first move...
              </p>
            ) : (
              movePairs.map((pair) => (
                <div
                  key={pair.turn}
                  className="flex justify-between py-1 px-2 rounded hover:bg-gray-700 transition-colors"
                >
                  <span className="text-gray-500 w-8">{pair.turn}.</span>
                  <span className="text-gray-200 flex-1">{pair.white}</span>
                  <span className="text-gray-400 flex-1">{pair.black}</span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
