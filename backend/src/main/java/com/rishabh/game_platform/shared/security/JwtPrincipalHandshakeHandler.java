package com.rishabh.game_platform.shared.security;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

public class JwtPrincipalHandshakeHandler extends DefaultHandshakeHandler {

    private static final String AUTHENTICATION_ATTRIBUTE = "jwtAuthentication";

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        Object authentication = attributes.get(AUTHENTICATION_ATTRIBUTE);
        if (authentication instanceof Authentication auth) {
            return auth;
        }

        return super.determineUser(request, wsHandler, attributes);
    }
}
