import React, { createContext, useContext, useEffect, useRef, useState, useCallback } from 'react';
import type { Client, StompSubscription } from '@stomp/stompjs';
import { createStompClient } from '../services/socket';

export type GameSessionPayload = {
  state?: {
    boardState?: string;
    [key: string]: unknown;
  };
  [key: string]: unknown;
};

interface WebSocketContextType {
  stompClient: Client | null;
  isConnected: boolean;
  subscribeToGame: (sessionId: string, callback: (payload: GameSessionPayload) => void) => StompSubscription | null;
  publishMove: (sessionId: string, sourceSquare: string, targetSquare: string, promotion?: string, username?: string) => void;
  requestGameState: (sessionId: string) => void;
}

const WebSocketContext = createContext<WebSocketContextType | null>(null);

export const WebSocketProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [stompClient, setStompClient] = useState<Client | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    const client = createStompClient();
    clientRef.current = client;

    client.onConnect = (frame) => {
      console.log('⚡ Connected to Spring Boot STOMP Broker', frame);
      setIsConnected(true);
    };

    client.onStompError = (frame) => {
      console.error('Broker reported error: ' + frame.headers['message']);
      console.error('Additional details: ' + frame.body);
    };

    client.onWebSocketClose = () => {
      console.log('Disconnected from Spring Boot');
      setIsConnected(false);
    };

    // Fire up the connection once globally
    client.activate();
    setStompClient(client);

    return () => {
      client.deactivate();
    };
  }, []);

  const subscribeToGame = useCallback(
    (sessionId: string, callback: (payload: GameSessionPayload) => void): StompSubscription | null => {
      const client = clientRef.current;

      if (!client || !client.active) {
        return null;
      }

      return client.subscribe(`/topic/game/${sessionId}`, (message) => {
        try {
          callback(JSON.parse(message.body) as GameSessionPayload);
        } catch (error) {
          console.error('Failed to parse game session payload:', error);
        }
      });
    },
    []
  );

  const publishMove = useCallback(
    (sessionId: string, sourceSquare: string, targetSquare: string, promotion?: string, username?: string) => {
      const client = clientRef.current;
      if (client && client.active) {
        const movePayload: { from: string; to: string; promotion?: string; username?: string } = {
          from: sourceSquare,
          to: targetSquare,
        };
        if (promotion) {
          movePayload.promotion = promotion;
        }
        if (username) {
          movePayload.username = username;
        }

        client.publish({
          destination: `/app/game/${sessionId}/move`,
          body: JSON.stringify(movePayload),
        });

        console.log('Move broadcasted to server:', movePayload);
      } else {
        console.error('STOMP client is not connected. Cannot send move.');
      }
    },
    []
  );

  const requestGameState = useCallback((sessionId: string) => {
    const client = clientRef.current;
    if (client?.connected) {
      client.publish({
        destination: `/app/game/${sessionId}/state`,
        body: '{}',
      });
    }
  }, []);

  return (
    <WebSocketContext.Provider
      value={{
        stompClient,
        isConnected,
        subscribeToGame,
        publishMove,
        requestGameState,
      }}
    >
      {children}
    </WebSocketContext.Provider>
  );
};

export const useWebSocketContext = () => {
  const context = useContext(WebSocketContext);
  if (!context) {
    throw new Error('useWebSocketContext must be used within a WebSocketProvider');
  }
  return context;
};
