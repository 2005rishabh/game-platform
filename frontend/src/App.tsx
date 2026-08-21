import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import Dashboard from "./pages/Dashboard";
import GameRoom from "./pages/GameRoom";
import Login from "./pages/Login";
import { useWebSocket } from "./hooks/useWebSocket";
import { WebSocketProvider } from "./context/WebSocketContext";

function AppContent() {
  const { isConnected } = useWebSocket();

  return (
    <Router>
      <div className="min-h-screen bg-[var(--color-midnight)] text-white font-sans selection:bg-[var(--color-premium-gold)] selection:text-[var(--color-midnight)]">
        {/* Global Connection Indicator */}
        <div className="absolute top-4 left-4 z-50 flex items-center gap-2 text-xs tracking-widest font-mono">
          <div
            className={`w-2 h-2 rounded-full ${isConnected ? "bg-green-500 animate-pulse" : "bg-red-500"}`}
          ></div>
          {isConnected ? "SYSTEM ONLINE" : "CONNECTING..."}
        </div>

        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/login" element={<Login />} />
          <Route path="/game/:sessionId" element={<GameRoom />} />
        </Routes>
      </div>
    </Router>
  );
}

function App() {
  return (
    <WebSocketProvider>
      <AppContent />
    </WebSocketProvider>
  );
}

export default App;
