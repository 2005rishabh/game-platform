package com.rishabh.game_platform.game.infrastructure.persistence.jpa;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.rishabh.game_platform.game.domain.enums.GameStatus;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.GameState;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.game.domain.ports.GameStateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Primary
@Repository
@RequiredArgsConstructor
public class JpaGameStateRepository implements GameStateRepository {

    private final SpringDataGameSessionRepository springDataRepository;
    private final ConcurrentHashMap<UUID, GameSession> cache = new ConcurrentHashMap<>();

    @Override
    public GameSession save(GameSession gameSession) {
        if (gameSession == null || gameSession.getGameId() == null) {
            return gameSession;
        }

        // 1. Save to in-memory cache for instant WebSocket responsiveness
        cache.put(gameSession.getGameId(), gameSession);

        // 2. Persist to PostgreSQL database
        try {
            String p1Name = gameSession.getPlayer1() != null ? gameSession.getPlayer1().getUsername() : null;
            String p2Name = gameSession.getPlayer2() != null ? gameSession.getPlayer2().getUsername() : null;
            String boardFen = gameSession.getState() != null ? gameSession.getState().getBoardState() : null;

            GameSessionEntity entity = GameSessionEntity.builder()
                    .gameId(gameSession.getGameId())
                    .gameType(gameSession.getGameType() != null ? gameSession.getGameType().name() : "CHESS")
                    .status(gameSession.getStatus() != null ? gameSession.getStatus().name() : "IN_PROGRESS")
                    .player1Username(p1Name)
                    .player2Username(p2Name)
                    .boardState(boardFen)
                    .createdAt(gameSession.getCreatedAt())
                    .build();

            springDataRepository.save(entity);
            log.info("Persisted GameSession {} to PostgreSQL", gameSession.getGameId());
        } catch (Exception e) {
            log.error("Failed to persist GameSession {} to PostgreSQL", gameSession.getGameId(), e);
        }

        return gameSession;
    }

    @Override
    public Optional<GameSession> findById(UUID gameId) {
        if (gameId == null) return Optional.empty();

        // Check cache first
        if (cache.containsKey(gameId)) {
            return Optional.ofNullable(cache.get(gameId));
        }

        // Fallback to PostgreSQL
        return springDataRepository.findById(gameId).map(entity -> {
            Player p1 = entity.getPlayer1Username() != null ? Player.builder().username(entity.getPlayer1Username()).build() : null;
            Player p2 = entity.getPlayer2Username() != null ? Player.builder().username(entity.getPlayer2Username()).build() : null;

            GameSession session = GameSession.builder()
                    .gameId(entity.getGameId())
                    .gameType(entity.getGameType() != null ? GameType.valueOf(entity.getGameType()) : GameType.CHESS)
                    .status(entity.getStatus() != null ? GameStatus.valueOf(entity.getStatus()) : GameStatus.IN_PROGRESS)
                    .player1(p1)
                    .player2(p2)
                    .state(GameState.builder().boardState(entity.getBoardState()).build())
                    .createdAt(entity.getCreatedAt())
                    .build();

            cache.put(gameId, session);
            return session;
        });
    }

    @Override
    public void deleteById(UUID gameId) {
        if (gameId == null) return;
        cache.remove(gameId);
        try {
            springDataRepository.deleteById(gameId);
        } catch (Exception e) {
            log.error("Failed to delete GameSession {} from PostgreSQL", gameId, e);
        }
    }
}
