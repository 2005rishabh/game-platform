package com.rishabh.game_platform.shared.security;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHENTICATION_SESSION_ATTRIBUTE = "SPRING_SECURITY_CONTEXT";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Authentication authentication = authenticate(accessor);
            if (authentication != null) {
                accessor.setUser(authentication);
                storeAuthentication(accessor, authentication);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        }

        if (accessor.getUser() == null) {
            Authentication authentication = restoreAuthentication(accessor);
            if (authentication != null) {
                accessor.setUser(authentication);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
            }
        }

        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);
        if (token == null || token.isBlank()) {
            return null;
        }

        String username = jwtService.extractUsername(token);
        if (username == null || username.isBlank()) {
            return null;
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (!jwtService.isTokenValid(token, userDetails)) {
            return null;
        }

        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities());
    }

    private void storeAuthentication(StompHeaderAccessor accessor, Authentication authentication) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            sessionAttributes = new HashMap<>();
            accessor.setSessionAttributes(sessionAttributes);
        }
        sessionAttributes.put(AUTHENTICATION_SESSION_ATTRIBUTE, authentication);
    }

    private Authentication restoreAuthentication(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return null;
        }

        Object storedAuthentication = sessionAttributes.get(AUTHENTICATION_SESSION_ATTRIBUTE);
        if (storedAuthentication instanceof Authentication authentication) {
            return authentication;
        }

        return null;
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
