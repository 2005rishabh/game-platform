package com.rishabh.game_platform.game.application.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import com.rishabh.game_platform.game.domain.events.GameEndedEvent;
import com.rishabh.game_platform.game.domain.ports.GameStateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service

public class GameEventConsumer {

    private final GameStateRepository gameStateRepository;
    private final UserRepository userRepository;

    @KafkaListener(topics = "game-ended-topic", groupId = "game-platform-group")

    public void consumeGameEndedEvent(GameEndedEvent event) {
        log.info("KAFKA CONSUMER CAUGHT EVENT: Game {} ended. Winner: {}. Reason: {}",
                event.gameId(), event.winnerId(), event.reason());

        // TODO: Update the GameSession status to FINISHED in PostgreSQL
        // TODO: Calculate ELO changes and update the users table
    }
}
