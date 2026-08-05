package com.rishabh.game_platform.game.infrastructure.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.rishabh.game_platform.shared.security.JwtChannelInterceptor;
import com.rishabh.game_platform.shared.security.JwtHandshakeInterceptor;
import com.rishabh.game_platform.shared.security.JwtPrincipalHandshakeHandler;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

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
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }

    @Bean
    public JwtPrincipalHandshakeHandler jwtPrincipalHandshakeHandler() {
        return new JwtPrincipalHandshakeHandler();
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // This is the initial handshake URL the React frontend uses to establish the
        // connection
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:5173", "http://127.0.0.1:5173") // <-- THE BOUNCER PASS
                .addInterceptors(jwtHandshakeInterceptor)
                .setHandshakeHandler(jwtPrincipalHandshakeHandler())
                .withSockJS();
    }
}
