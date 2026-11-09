package com.rishabh.game_platform.game.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SpringDataProcessedGameEventRepository
    extends JpaRepository<ProcessedGameEventEntity, UUID>
{
    @Transactional
    @Modifying
    @Query(
        value = "INSERT INTO processed_game_events (game_id, processed_at) VALUES (:gameId, :processedAt) ON CONFLICT DO NOTHING",
        nativeQuery = true
    )
    int insertIfNotExists(
        @Param("gameId") UUID gameId,
        @Param("processedAt") Instant processedAt
    );
}
