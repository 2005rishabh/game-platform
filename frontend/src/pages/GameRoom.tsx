import { useParams } from "react-router-dom";

export default function GameRoom() {
  const { sessionId } = useParams();

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-[var(--color-midnight)]">
      <h1 className="text-2xl font-serif text-[var(--color-premium-gold)] tracking-widest mb-4">
        Match Initialized
      </h1>
      <p className="text-gray-400 font-mono tracking-widest text-sm">
        Session: {sessionId}
      </p>
    </div>
  );
}
