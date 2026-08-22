package com.rishabh.game_platform.game.infrastructure.persistence.jpa;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataGameSessionRepository extends JpaRepository<GameSessionEntity, UUID> {
}
