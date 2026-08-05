import { useState, useEffect } from "react";
import { useParams } from "react-router-dom";
import { Chess } from "chess.js";
import { Chessboard } from "react-chessboard";
import { useWebSocket } from "../hooks/useWebSocket";

export default function GameRoom() {
  const { sessionId } = useParams();

  // Extract all the necessary variables and functions from the hook
  const { isConnected, publishMove, subscribeToGame, stompClient } =
    useWebSocket();

  // Initialize the headless chess engine
  const [game, setGame] = useState(new Chess());

  // 📥 NEW: Listen for opponent moves and authoritative state from the server
  useEffect(() => {
    // Wait until the connection is fully established before subscribing
    if (!isConnected || !sessionId || !stompClient || !subscribeToGame) return;

    console.log("Subscribing to game room channel: ", sessionId);

    const subscription = subscribeToGame(sessionId, (incomingSession) => {
      console.log(
        "📥 Authoritative game state received from server!",
        incomingSession,
      );

      // Verify the payload structure matches your Java GameSession & GameState models
      if (
        incomingSession &&
        incomingSession.state &&
        incomingSession.state.boardState
      ) {
        setGame((currentGame) => {
          const gameCopy = new Chess();
          try {
            // Force the local UI board to exactly match the server's FEN string
            gameCopy.load(incomingSession.state.boardState);
            return gameCopy;
          } catch (e) {
            console.error("Failed to load authoritative FEN from server:", e);
            return currentGame;
          }
        });
      }
    });

    // Cleanup: Unsubscribe when the player leaves the room/component unmounts
    return () => {
      if (subscription) subscription.unsubscribe();
    };
  }, [isConnected, sessionId, stompClient, subscribeToGame]);

  function onDrop({
    sourceSquare,
    targetSquare,
  }: {
    sourceSquare: string;
    targetSquare: string | null;
  }) {
    if (!targetSquare) {
      return false;
    }

    console.log(`Attempting to move from ${sourceSquare} to ${targetSquare}`);

    try {
      const gameCopy = new Chess(game.fen());
      const move = gameCopy.move({
        from: sourceSquare,
        to: targetSquare,
        promotion: "q",
      });

      if (move === null) {
        console.warn("chess.js rejected the move as illegal.");
        return false;
      }

      setGame(gameCopy);
      console.log("Move successful on the local board!");

      // 🚀 FIRE THE MOVE TO THE SPRING BOOT BACKEND
      if (sessionId && publishMove) {
        publishMove(sessionId, sourceSquare, targetSquare);
      }

      return true;
    } catch (error) {
      console.error("Critical crash in onDrop:", error);
      return false;
    }
  }

  const boardOptions = {
    position: game.fen(),
    onPieceDrop: onDrop,
    allowDragging: true,
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
            ID: {sessionId}
          </p>
        </div>
        <div className="flex flex-col items-end">
          <span className="text-xs tracking-[0.2em] text-gray-400 uppercase mb-1">
            Server Status
          </span>
          <div className="flex items-center gap-2">
            <div
              className={`w-2 h-2 rounded-full ${isConnected ? "bg-green-500 animate-pulse" : "bg-red-500"}`}
            ></div>
            <span className="text-sm font-bold tracking-widest text-[var(--color-premium-gold)]">
              {isConnected ? "SYNCED" : "DISCONNECTED"}
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
