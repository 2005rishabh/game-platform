import { useEffect, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { createStompClient } from '../services/socket';

export const useWebSocket = () => {
  const [stompClient, setStompClient] = useState<Client | null>(null);
  const [isConnected, setIsConnected] = useState(false);

  useEffect(() => {
    const client = createStompClient();

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

  return { stompClient, isConnected };
};