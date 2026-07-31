package com.rishabh.game_platform.game.infrastructure.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Clients (React) will subscribe to endpoints starting with "/topic" to receive
        // data.
        // Example: /topic/game/{sessionId}
        registry.enableSimpleBroker("/topic");

        // Clients will send data to endpoints starting with "/app".
        // Example: /app/game.move
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // This is the initial handshake URL the React frontend uses to establish the
        // connection
        registry.addEndpoint("/ws-game")
                .setAllowedOriginPatterns("*") // Allows cross-origin requests from your frontend
                .withSockJS();
    }
}
