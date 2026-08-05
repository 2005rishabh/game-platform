import { useState } from "react";
import { useParams } from "react-router-dom";
import { Chess } from "chess.js";
import { Chessboard } from "react-chessboard";
import { useWebSocket } from "../hooks/useWebSocket";

export default function GameRoom() {
  const { sessionId } = useParams();
  const { isConnected } = useWebSocket();

  // Initialize the headless chess engine
  const [game, setGame] = useState(new Chess());

  // Function to handle when a player drops a piece
  function onDrop(sourceSquare: string, targetSquare: string) {
    // Create a copy of the game state to mutate safely
    const gameCopy = new Chess(game.fen());

    try {
      // Attempt to make the move locally
      const move = gameCopy.move({
        from: sourceSquare,
        to: targetSquare,
        promotion: "q", // Automatically promote to Queen for now
      });

      // If the move is illegal, chess.js throws an error or returns null
      if (move === null) return false;

      // Update the local board state
      setGame(gameCopy);

      // TODO: Publish this move to the Spring Boot backend via STOMP here
      // stompClient.publish({ destination: `/app/game/${sessionId}/move`, body: ... })

      return true;
    } catch (error) {
      // Illegal move attempted
      return false;
    }
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-screen p-4 bg-[var(--color-midnight)]">
      {/* Top Bar: Match Info */}
      <div className="w-full max-w-3xl flex justify-between items-center mb-8 px-6 py-4 bg-[var(--color-charcoal)] border border-gray-800 rounded-sm shadow-xl">
        <div>
          <h2 className="text-xl font-serif text-white tracking-widest uppercase">
            Ranked Match
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
          <Chessboard
            // @ts-expect-error - TS is failing to load react-chessboard types
            position={game.fen()}
            onPieceDrop={onDrop}
            customDarkSquareStyle={{ backgroundColor: "#475569" }}
            customLightSquareStyle={{ backgroundColor: "#94A3B8" }}
            animationDuration={300}
          />
        </div>
        {/* Move History Sidebar */}
        <div className="w-full md:w-64 h-[500px] bg-[var(--color-charcoal)] border border-gray-800 rounded-sm p-4 flex flex-col shadow-xl">
          <h3 className="text-sm font-serif text-[var(--color-premium-gold)] tracking-[0.2em] uppercase mb-4 border-b border-gray-700 pb-2">
            Live Notation
          </h3>
          <div className="flex-1 overflow-y-auto font-mono text-sm text-gray-300 space-y-2">
            {/* We will map over actual moves here later */}
            <p className="text-gray-500 italic text-xs text-center mt-4">
              Waiting for first move...
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
