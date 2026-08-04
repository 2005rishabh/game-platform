import React, { useState } from "react";
import { useNavigate } from "react-router-dom";

export const Dashboard = () => {
  const [isSearching, setIsSearching] = useState(false);
  const navigate = useNavigate();

  const handleFindMatch = () => {
    setIsSearching(true);
    // Simulate a matchmaking queue delay for now
    setTimeout(() => {
      const mockSessionId = crypto.randomUUID();
      navigate(`/game/${mockSessionId}`);
    }, 2500);
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
          disabled={isSearching}
          className={`
            px-12 py-4 rounded-sm text-sm tracking-[0.2em] uppercase font-bold transition-all duration-300 border
            ${
              isSearching
                ? "bg-transparent border-[var(--color-premium-gold)]/30 text-[var(--color-premium-gold)]/50 cursor-not-allowed"
                : "bg-gradient-to-b from-[var(--color-premium-gold)] to-[#AA8C2C] border-[#F3E5AB] text-[var(--color-charcoal)] hover:shadow-[0_0_20px_rgba(212,175,55,0.3)] hover:scale-[1.02]"
            }
          `}
        >
          {isSearching ? "Searching..." : "Find Match"}
        </button>

        <button className="text-xs tracking-[0.15em] text-gray-400 hover:text-white uppercase transition-colors">
          Create Custom Room &rarr;
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
            onClick={() => setIsSearching(false)}
            className="mt-12 text-xs tracking-[0.15em] text-gray-500 hover:text-white uppercase border border-gray-800 px-6 py-2 rounded-sm transition-colors"
          >
            Cancel Search
          </button>
        </div>
      )}
    </div>
  );
};
