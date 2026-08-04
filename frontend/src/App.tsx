import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import GameRoom from "./pages/GameRoom";
import { Dashboard } from "./pages/Dashboard";

export const App = () => {
  return (
    <Router>
      <div className="min-h-screen bg-[var(--color-midnight)] text-white font-sans selection:bg-[var(--color-premium-gold)] selection:text-[var(--color-midnight)]">
        <Routes>
          <Route>
            <Route path="/" element={<Dashboard />} />
            <Route path="/game/:sessionId" element={<GameRoom />} />
          </Route>
        </Routes>
      </div>
    </Router>
  );
};

export default App;
