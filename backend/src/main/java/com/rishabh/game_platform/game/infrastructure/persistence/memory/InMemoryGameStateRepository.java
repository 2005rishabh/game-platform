package com.rishabh.game_platform.game.infrastructure.persistence.memory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.ports.GameStateRepository;

public class InMemoryGameStateRepository implements GameStateRepository{
    private final ConcurrentHashMap<UUID, GameSession> activeGames = new ConcurrentHashMap<>();

    @Override
    public GameSession save(GameSession gameSession) {
        activeGames.put(gameSession.getGameId(), gameSession);
        return gameSession;
    }

    @Override
    public Optional<GameSession> findById(UUID gameId) {
        return Optional.ofNullable(activeGames.get(gameId));
    }

    @Override
    public void deleteById(UUID gameId) {
        activeGames.remove(gameId);
    }

    
}
