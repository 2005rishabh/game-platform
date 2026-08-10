import { useState, useEffect } from "react";
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

  // NEW: State for player color and turn enforcement
  const [playerColor, setPlayerColor] = useState<"white" | "black">("white");
  const [isMyTurn, setIsMyTurn] = useState<boolean>(false);

  // NEW: Track if we have received the initial state from the server
  const [hasReceivedState, setHasReceivedState] = useState<boolean>(false);

  // NEW: Grab the logged-in username to determine if they are Player 1 (White) or Player 2 (Black)
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

        // REMOVED the `if (boardState)` wrapper so it ALWAYS calculates the turn
        setGame((currentGame) => {
          const gameCopy = new Chess();
          try {
            // If the server sends a saved board state, load it.
            // If not, gameCopy just stays at the default starting position.
            if (boardState) {
              gameCopy.load(boardState);
            }

            // 3. Update Turn Status (chess.js uses 'w' and 'b')
            const activeTurnColor = gameCopy.turn() === "w" ? "white" : "black";
            setIsMyTurn(activeTurnColor === myRole);

            return gameCopy;
          } catch (e) {
            console.error("Failed to load authoritative FEN from server:", e);
            return currentGame;
          }
        });
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
    // NEW: Block moves if it's not the user's turn (UNLESS we are waiting for the server to wake up!)
    if (!targetSquare || (!isMyTurn && hasReceivedState)) {
      console.warn("Not your turn!");
      return false;
    }

    console.log(`Attempting to move from ${sourceSquare} to ${targetSquare}`);

    try {
      const gameCopy = new Chess(game.fen());
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

      setGame(gameCopy);
      console.log("Move successful on the local board!");

      // 🚀 FIRE THE MOVE TO THE SPRING BOOT BACKEND
      if (sessionId && publishMove) {
        publishMove(sessionId, sourceSquare, targetSquare, promotionChoice);
      }

      return true;
    } catch (error) {
      console.error("Error in onDrop:", error);
      return false;
    }
  }

  // NEW: Updated boardOptions with boardOrientation and allowDragging logic
  const boardOptions = {
    position: game.fen(),
    onPieceDrop: onDrop,
    boardOrientation: playerColor,
    // NEW: Allow dragging if it's our turn, OR if we are waiting for the server to wake up
    allowDragging: isMyTurn || !hasReceivedState,
    boardStyle: { touchAction: "none" },
    darkSquareStyle: { backgroundColor: "#475569" },
    lightSquareStyle: { backgroundColor: "#94A3B8" },
    animationDurationInMs: 300,
  };

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
          <div className="flex-1 overflow-y-auto font-mono text-sm text-gray-300 space-y-2">
            <p className="text-gray-500 italic text-xs text-center mt-4">
              Waiting for first move...
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
