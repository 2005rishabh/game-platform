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

  // Instead of hardcoding 'http://localhost:8080/ws':
  const rawApiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080';

  // Normalize environment input (strip surrounding brackets or markdown links)
  const sanitize = (u: string) => {
    if (!u) return u;
    // If user provided markdown-style [text](url), extract url
    const mdMatch = u.match(/\((https?:\/\/[^)]+)\)/);
    if (mdMatch) return mdMatch[1];
    // Remove any surrounding brackets
    return u.replace(/^\[+|\]+$/g, '');
  };

  const baseUrl = sanitize(rawApiUrl).replace(/\/$/, '');
  const WS_ENDPOINT = `${baseUrl}/ws`;

  const client = new Client({
    // We use SockJS as a fallback and to match the Spring Boot configuration
    webSocketFactory: () => new SockJS(WS_ENDPOINT),
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