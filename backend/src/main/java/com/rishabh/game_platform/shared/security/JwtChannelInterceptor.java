package com.rishabh.game_platform.shared.security;

import java.util.List;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String token = resolveToken(accessor);
        if (token == null || token.isBlank()) {
            return message;
        }

        String username = jwtService.extractUsername(token);
        if (username == null || username.isBlank()) {
            return message;
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (!jwtService.isTokenValid(token, userDetails)) {
            return message;
        }

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities());
        accessor.setUser(authToken);
        SecurityContextHolder.getContext().setAuthentication(authToken);

        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        List<String> authorizationHeaders = accessor.getNativeHeader("Authorization");
        if (authorizationHeaders != null && !authorizationHeaders.isEmpty()) {
            String headerValue = authorizationHeaders.get(0);
            if (headerValue != null && headerValue.startsWith("Bearer ")) {
                return headerValue.substring(7).trim();
            }
        }

        List<String> fallbackHeaders = accessor.getNativeHeader("X-Authorization");
        if (fallbackHeaders != null && !fallbackHeaders.isEmpty()) {
            String headerValue = fallbackHeaders.get(0);
            if (headerValue != null && headerValue.startsWith("Bearer ")) {
                return headerValue.substring(7).trim();
            }
        }

        return null;
    }
}
