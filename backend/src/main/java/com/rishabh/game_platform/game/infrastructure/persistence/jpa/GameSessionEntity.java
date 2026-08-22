package com.rishabh.game_platform.game.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "game_sessions")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameSessionEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID gameId;

    @Column(nullable = false)
    private String gameType;

    @Column(nullable = false)
    private String status;

    private String player1Username;
    private String player2Username;

    @Column(columnDefinition = "TEXT")
    private String boardState;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
