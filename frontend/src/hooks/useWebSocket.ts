import { useWebSocketContext } from '../context/WebSocketContext';

export const useWebSocket = () => {
  return useWebSocketContext();
};