import { useEffect, useRef, useState } from 'react';
import { Client, StompSubscription } from '@stomp/stompjs';
import { createStompClient } from '../services/socket';

type GameSessionPayload = {
  state?: {
    boardState?: string;
    [key: string]: unknown;
  };
  [key: string]: unknown;
};

export const useWebSocket = () => {
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

    // Fire up the connection
    client.activate();
    setStompClient(client);

    // Cleanup function when the component unmounts
    return () => {
      client.deactivate();
    };
  }, []);

  const subscribeToGame = (sessionId: string, callback: (payload: GameSessionPayload) => void): StompSubscription | null => {
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
  };

  const publishMove = (sessionId: string, sourceSquare: string, targetSquare: string) => {
    // Ensure the client exists and is actually connected before sending
    if (stompClient && stompClient.active) {
      const movePayload = {
        from: sourceSquare,    // Changed to match MoveRequest.java
        to: targetSquare,      // Changed to match MoveRequest.java
        promotion: "q"
      };

      stompClient.publish({
        destination: `/app/game/${sessionId}/move`,
        body: JSON.stringify(movePayload),
      });
      
      console.log("Move broadcasted to server:", movePayload);
    } else {
      console.error("STOMP client is not connected. Cannot send move.");
    }
  };

  return { stompClient, isConnected, publishMove, subscribeToGame };
};