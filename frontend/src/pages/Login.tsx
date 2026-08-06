import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { authService } from "../services/authService";

export default function Login() {
  const [isRegister, setIsRegister] = useState(false);
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      if (isRegister) {
        await authService.register({ username, email, password });
      } else {
        await authService.login({ username, password });
      }
      navigate("/");
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError("An unexpected error occurred.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-screen px-4 bg-[var(--color-midnight)]">
      <div className="w-full max-w-md p-8 bg-[var(--color-charcoal)] border border-gray-800 rounded-sm shadow-2xl space-y-6">
        <div className="text-center space-y-2">
          <h1 className="text-3xl font-serif tracking-widest text-white uppercase drop-shadow">
            Grandmaster Duel
          </h1>
          <p className="text-xs tracking-[0.2em] text-[var(--color-premium-gold)] uppercase font-semibold">
            {isRegister ? "Create Account" : "Sign In to Play"}
          </p>
        </div>

        {error && (
          <div className="p-3 text-xs tracking-wide bg-red-900/30 border border-red-800 text-red-300 rounded-sm">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs tracking-[0.15em] text-gray-400 uppercase mb-1">
              Username
            </label>
            <input
              type="text"
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full px-4 py-2 text-sm bg-black/40 border border-gray-700 rounded-sm text-white focus:outline-none focus:border-[var(--color-premium-gold)] font-mono"
              placeholder="e.g. grandmaster_alex"
            />
          </div>

          {isRegister && (
            <div>
              <label className="block text-xs tracking-[0.15em] text-gray-400 uppercase mb-1">
                Email
              </label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full px-4 py-2 text-sm bg-black/40 border border-gray-700 rounded-sm text-white focus:outline-none focus:border-[var(--color-premium-gold)] font-mono"
                placeholder="alex@example.com"
              />
            </div>
          )}

          <div>
            <label className="block text-xs tracking-[0.15em] text-gray-400 uppercase mb-1">
              Password
            </label>
            <input
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-4 py-2 text-sm bg-black/40 border border-gray-700 rounded-sm text-white focus:outline-none focus:border-[var(--color-premium-gold)] font-mono"
              placeholder="••••••••"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className={`
              w-full py-3 mt-4 text-xs font-bold tracking-[0.2em] uppercase rounded-sm border transition-all duration-300
              ${
                loading
                  ? "bg-transparent border-[var(--color-premium-gold)]/30 text-[var(--color-premium-gold)]/50 cursor-not-allowed"
                  : "bg-gradient-to-b from-[var(--color-premium-gold)] to-[#AA8C2C] border-[#F3E5AB] text-[var(--color-charcoal)] hover:shadow-[0_0_20px_rgba(212,175,55,0.3)] hover:scale-[1.01]"
              }
            `}
          >
            {loading ? "Authenticating..." : isRegister ? "Register" : "Sign In"}
          </button>
        </form>

        <div className="pt-4 border-t border-gray-800 text-center">
          <button
            onClick={() => {
              setIsRegister(!isRegister);
              setError(null);
            }}
            className="text-xs tracking-[0.15em] text-gray-400 hover:text-[var(--color-premium-gold)] transition-colors uppercase"
          >
            {isRegister
              ? "Already have an account? Sign In"
              : "Need an account? Register"}
          </button>
        </div>
      </div>
    </div>
  );
}
