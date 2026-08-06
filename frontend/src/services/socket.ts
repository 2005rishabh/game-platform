import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const getStoredToken = () => {
  if (typeof window === 'undefined') {
    return null;
  }

  const storedToken = localStorage.getItem('token')
    ?? localStorage.getItem('jwt')
    ?? localStorage.getItem('accessToken')
    ?? localStorage.getItem('access_token');
  return storedToken ?? null;
};

export const createStompClient = (token?: string) => {
  const getHeaders = () => {
    const storedToken = token ?? getStoredToken();
    const bearerToken = storedToken?.startsWith('Bearer ') ? storedToken : storedToken ? `Bearer ${storedToken}` : null;
    return bearerToken ? { Authorization: bearerToken } : undefined;
  };

  const client = new Client({
    // We use SockJS as a fallback and to match the Spring Boot configuration
    webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
    connectHeaders: getHeaders(),

    // This will print STOMP frames to browser console for debugging
    debug: (msg: string) => console.log(msg),

    // Automatically try to reconnect if the Java server restarts
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
  });

  client.beforeConnect = () => {
    const headers = getHeaders();
    if (headers) {
      client.connectHeaders = headers;
    }
  };

  return client;
};