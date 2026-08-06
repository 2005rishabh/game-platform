package com.rishabh.game_platform.game.api;

import java.security.Principal;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rishabh.game_platform.auth.infrastructure.persistence.UserEntity;
import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import com.rishabh.game_platform.game.api.dto.CreateGameRequest;
import com.rishabh.game_platform.game.application.service.GameService;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.Player;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor

public class GameController {
    private final GameService gameService;
    private final UserRepository userRepository;

    @PostMapping("/create")
    public ResponseEntity<GameSession> createGame(@RequestBody CreateGameRequest request, Principal principal) {
        Player host = getAuthenticatedPlayer(principal);
        GameSession gameSession = gameService.createGame(host, request.getGameType());

        return ResponseEntity.ok(gameSession);
    }

    @PostMapping("/{sessionId}/join")
    public ResponseEntity<GameSession> joinGame(@PathVariable UUID sessionId, Principal principal) {
        Player guest = getAuthenticatedPlayer(principal);
        GameSession session = gameService.joinGame(sessionId, guest);
        return ResponseEntity.ok(session);
    }

    /*
    *Helper funtion to map a logged in Spring Security to our Domain Player
    */

    private Player getAuthenticatedPlayer(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalArgumentException("Authenticated principal is required");
        }

        String username = principal.getName();
        return userRepository.findByUsername(username)
                .map(user -> Player.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .eloRating(user.getEloRating() != null ? user.getEloRating() : 1200)
                        .build())
                .orElseGet(() -> Player.builder()
                        .username(username)
                        .eloRating(1200)
                        .build());
    }
}
