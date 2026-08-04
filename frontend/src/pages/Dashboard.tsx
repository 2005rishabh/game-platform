import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useWebSocket } from "../hooks/useWebSocket";

export default function Dashboard() {
  const [isSearching, setIsSearching] = useState(false);
  const { stompClient, isConnected } = useWebSocket();
  const navigate = useNavigate();

  // Generate a mock Player ID for now (until I wire up JWT login)
  const [playerId] = useState(() => crypto.randomUUID());

  useEffect(() => {
    // If the user cancels the search or leaves the page, make sure to clean up
    return () => {
      if (isSearching && stompClient && stompClient.connected) {
        stompClient.publish({
          destination: "/app/matchmaking.cancel",
          body: JSON.stringify({ playerId }),
        });
      }
    };
  }, [isSearching, stompClient, playerId]);

  const handleFindMatch = () => {
    if (!isConnected || !stompClient) {
      console.error("Cannot search for match: WebSocket disconnected.");
      return;
    }

    setIsSearching(true);

    // 1. Subscribe to a unique channel to listen for our match
    const subscription = stompClient.subscribe(
      `/topic/match/${playerId}`,
      (message) => {
        const matchData = JSON.parse(message.body);

        console.log("Match found!", matchData);

        // Clean up the subscription since we found a match
        subscription.unsubscribe();

        // Route the user to the live game room
        navigate(`/game/${matchData.sessionId}`);
      },
    );

    // 2. Publish our request to join the queue
    stompClient.publish({
      destination: "/app/matchmaking.join",
      body: JSON.stringify({
        playerId: playerId,
        username: "Guest_" + playerId.substring(0, 4),
        eloRating: 1200,
      }),
    });
  };

  const handleCancelSearch = () => {
    setIsSearching(false);
    if (stompClient && stompClient.connected) {
      stompClient.publish({
        destination: "/app/matchmaking.cancel",
        body: JSON.stringify({ playerId }),
      });
    }
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-screen px-4 bg-[var(--color-charcoal)]">
      {/* Header Section */}
      <div className="text-center mb-16 space-y-4">
        <h1 className="text-5xl md:text-7xl font-serif tracking-widest text-white uppercase drop-shadow-lg">
          Grandmaster Duel
        </h1>
        <p className="text-sm md:text-base tracking-[0.2em] text-gray-400 uppercase">
          Strategy is the art of controlled chaos.
        </p>
      </div>

      {/* Primary Action */}
      <div className="flex flex-col items-center space-y-6">
        <button
          onClick={handleFindMatch}
          disabled={isSearching || !isConnected}
          className={`
            px-12 py-4 rounded-sm text-sm tracking-[0.2em] uppercase font-bold transition-all duration-300 border
            ${
              isSearching || !isConnected
                ? "bg-transparent border-[var(--color-premium-gold)]/30 text-[var(--color-premium-gold)]/50 cursor-not-allowed"
                : "bg-gradient-to-b from-[var(--color-premium-gold)] to-[#AA8C2C] border-[#F3E5AB] text-[var(--color-charcoal)] hover:shadow-[0_0_20px_rgba(212,175,55,0.3)] hover:scale-[1.02]"
            }
          `}
        >
          {isSearching
            ? "Searching..."
            : !isConnected
              ? "Connecting..."
              : "Find Match"}
        </button>
      </div>

      {/* Tension Queue Overlay */}
      {isSearching && (
        <div className="fixed inset-0 bg-[var(--color-midnight)]/95 backdrop-blur-md flex flex-col items-center justify-center z-50">
          <div className="relative flex items-center justify-center mb-8">
            <div className="absolute w-24 h-24 border border-[var(--color-premium-gold)]/20 rounded-full animate-ping"></div>
            <div className="w-16 h-16 border-t-2 border-r-2 border-[var(--color-premium-gold)] rounded-full animate-spin"></div>
          </div>
          <p className="text-xl font-serif tracking-widest text-white animate-pulse">
            Searching for opponent...
          </p>
          <button
            onClick={handleCancelSearch}
            className="mt-12 text-xs tracking-[0.15em] text-gray-500 hover:text-white uppercase border border-gray-800 px-6 py-2 rounded-sm transition-colors"
          >
            Cancel Search
          </button>
        </div>
      )}
    </div>
  );
}
