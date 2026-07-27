package com.rishabh.game_platform.auth.application;

import java.util.ArrayList;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.rishabh.game_platform.auth.api.dto.AuthResponse;
import com.rishabh.game_platform.auth.api.dto.LoginRequest;
import com.rishabh.game_platform.auth.api.dto.RegisterRequest;
import com.rishabh.game_platform.auth.infrastructure.persistence.UserEntity;
import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import com.rishabh.game_platform.shared.security.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor

public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username is already taken");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email is already in use");
        }

        UserEntity userEntity = UserEntity.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(userEntity);

        User jwtUser = new User(userEntity.getUsername(), userEntity.getPassword(), new ArrayList<>());
        String token = jwtService.generateToken(jwtUser);

        return AuthResponse.builder()
                .token(token)
                .username(userEntity.getUsername())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()));
        UserEntity userEntity = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        User jwtUser = new User(userEntity.getUsername(), userEntity.getPassword(), new ArrayList<>());
        String token = jwtService.generateToken(jwtUser);

        return AuthResponse.builder()
                .token(token)
                .username(userEntity.getUsername())
                .build();
    }
}
