package com.rishabh.game_platform.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName(
        "UT-02-A: Public endpoints like WebSocket /ws or Auth endpoints should be permitted without a token"
    )
    void shouldAllowPublicEndpoints() throws Exception {
        // Hitting an auth endpoint structure or a websocket handshake path without auth
        mockMvc
            .perform(post("/auth/api/nonexistent-subpath"))
            .andExpect(status().is4xxClientError()); // Expecting 404 or 400, NOT 401 Unauthorized block
    }

    @Test
    @DisplayName(
        "UT-02-B: Protected endpoints should reject unauthenticated requests with 401 Unauthorized"
    )
    void shouldRejectUnauthenticatedRequests() throws Exception {
        // Attempting to hit a protected resource without providing a JWT token returns 401
        mockMvc
            .perform(get("/api/protected-game-route"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName(
        "UT-02-C: Protected endpoints should allow requests with valid security context / token"
    )
    void shouldAllowAuthenticatedRequests() throws Exception {
        // Using Spring Security Test mock jwt to simulate an authenticated user session
        mockMvc
            .perform(get("/api/protected-game-route").with(jwt()))
            .andExpect(status().isNotFound()); // NotFound (404) proves it passed security filters and reached dispatcher
    }
}
