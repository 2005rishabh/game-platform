package com.rishabh.game_platform.auth;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rishabh.game_platform.auth.infrastructure.persistence.UserEntity;
import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import com.rishabh.game_platform.shared.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.security.Key;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityBearerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("UT-02: Missing Bearer token receives 401")
    void rejectsMissingTokenWithUnauthorized() throws Exception {
        mockMvc
            .perform(
                post("/api/games/create")
                    .contentType(APPLICATION_JSON)
                    .content("{\"gameType\":\"CHESS\"}")
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("UT-02: Expired Bearer token receives 401")
    void rejectsExpiredTokenWithUnauthorized() throws Exception {
        String username = createUser();
        Key signingKey = ReflectionTestUtils.invokeMethod(
            jwtService,
            "getSignInKey"
        );
        String expiredToken = Jwts.builder()
            .setSubject(username)
            .setIssuedAt(new Date(System.currentTimeMillis() - 60_000))
            .setExpiration(new Date(System.currentTimeMillis() - 30_000))
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();

        mockMvc
            .perform(
                post("/api/games/create")
                    .header("Authorization", "Bearer " + expiredToken)
                    .contentType(APPLICATION_JSON)
                    .content("{\"gameType\":\"CHESS\"}")
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName(
        "UT-02: Valid Bearer token reaches the protected game endpoint"
    )
    void acceptsValidToken() throws Exception {
        String username = createUser();
        String token = jwtService.generateToken(
            User.withUsername(username)
                .password("unused")
                .authorities(List.of())
                .build()
        );

        mockMvc
            .perform(
                post("/api/games/create")
                    .header("Authorization", "Bearer " + token)
                    .contentType(APPLICATION_JSON)
                    .content("{\"gameType\":\"CHESS\"}")
            )
            .andExpect(status().isOk());
    }

    private String createUser() {
        String username = "security-" + UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(username + "@test.invalid");
        user.setPassword("unused");
        user.setEloRating(1200);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.saveAndFlush(user);
        return username;
    }
}
