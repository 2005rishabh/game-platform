package com.rishabh.game_platform.game.application.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.rishabh.game_platform.game.domain.events.GameEndedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String GAME_ENDED_TOPIC = "game-ended-topic";

    public void publishGameEnded(GameEndedEvent event) {
        if (event == null || event.gameId() == null) {
            log.warn("Cannot publish null GameEndedEvent or missing gameId");
            return;
        }

        String messageKey = event.gameId().toString();

        // Using gameId as key to guarantee partition order in Kafka
        kafkaTemplate.send(GAME_ENDED_TOPIC, messageKey, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Successfully published GameEndedEvent to Kafka for Game ID: {}, Winner: {}, Loser: {}",
                                event.gameId(), event.winnerId(), event.loserId());
                    } else {
                        log.error("Failed to publish GameEndedEvent to Kafka for Game ID: {}", event.gameId(), ex);
                    }
                });
    }
}
