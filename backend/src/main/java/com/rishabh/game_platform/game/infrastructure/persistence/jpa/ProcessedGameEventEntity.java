package com.rishabh.game_platform.game.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_game_events")
public class ProcessedGameEventEntity {

    @Id
    @Column(name = "game_id", nullable = false, updatable = false)
    private UUID gameId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedGameEventEntity() {}

    public ProcessedGameEventEntity(UUID gameId, Instant processedAt) {
        this.gameId = gameId;
        this.processedAt = processedAt;
    }

    public UUID getGameId() {
        return gameId;
    }

    public void setGameId(UUID gameId) {
        this.gameId = gameId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
