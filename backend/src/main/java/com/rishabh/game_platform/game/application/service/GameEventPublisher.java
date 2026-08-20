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
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    private static final String GAME_ENDED_TOPIC = "game-ended-topic";

    public void publishGameEnded(GameEndedEvent event) {
        // Using the gameId as the Kafka key to ensure events for the same game stay in order
        kafkaTemplate.send(GAME_ENDED_TOPIC, event.gameId().toString(), event);
        log.info("Published GameEndedEvent to Kafka for Game ID: {}", event.gameId());
    }
}
