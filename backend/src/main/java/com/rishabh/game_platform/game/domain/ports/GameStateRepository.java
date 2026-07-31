package com.rishabh.game_platform.game.domain.ports;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.rishabh.game_platform.game.domain.model.GameSession;

public interface GameStateRepository {
    GameSession save(GameSession gameSession);

    Optional<GameSession> findById(UUID gameId);

    void deleteById(UUID gameId);
}