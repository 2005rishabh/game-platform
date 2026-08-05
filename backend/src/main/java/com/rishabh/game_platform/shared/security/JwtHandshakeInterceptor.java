package com.rishabh.game_platform.shared.security;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final String AUTHENTICATION_ATTRIBUTE = "jwtAuthentication";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = resolveToken(request.getHeaders());
        if (token == null || token.isBlank()) {
            return true;
        }

        Authentication authentication = authenticate(token);
        if (authentication == null) {
            return true;
        }

        attributes.put(AUTHENTICATION_ATTRIBUTE, authentication);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private Authentication authenticate(String token) {
        String username = jwtService.extractUsername(token);
        if (username == null || username.isBlank()) {
            return null;
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (!jwtService.isTokenValid(token, userDetails)) {
            return null;
        }

        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private String resolveToken(HttpHeaders headers) {
        List<String> authorizationHeaders = headers.get("Authorization");
        if (authorizationHeaders != null && !authorizationHeaders.isEmpty()) {
            String headerValue = authorizationHeaders.get(0);
            if (headerValue != null && headerValue.startsWith("Bearer ")) {
                return headerValue.substring(7).trim();
            }
        }

        return null;
    }
}
