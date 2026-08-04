import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export const createStompClient = () => {
  return new Client({
    // We use SockJS as a fallback and to match the Spring Boot configuration
    webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
    
    // This will print STOMP frames to browser console for debugging
    debug: (msg: string) => console.log(msg),
    
    // Automatically try to reconnect if the Java server restarts
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
  });
};