package com.rishabh.game_platform.game.domain.ports;

import java.util.Optional;
import java.util.UUID;

import com.rishabh.game_platform.game.domain.model.GameSession;

public interface GameStateRepository {
    void save(GameSession gameSession);

    Optional<GameSession> findById(UUID gameId);

    void deleteById(UUID gameId);
}