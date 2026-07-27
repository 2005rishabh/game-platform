package com.rishabh.game_platform.auth.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rishabh.game_platform.auth.api.dto.AuthResponse;
import com.rishabh.game_platform.auth.api.dto.LoginRequest;
import com.rishabh.game_platform.auth.api.dto.RegisterRequest;
import com.rishabh.game_platform.auth.application.AuthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth/api")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/request")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        AuthResponse authResponse = authService.register(request);
        return ResponseEntity.ok(authResponse);
    }
}
