package com.rishabh.game_platform.game;

import com.rishabh.game_platform.game.domain.events.GameEndedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 1, brokerProperties = { "listeners=PLAINTEXT://localhost:9092", "port=9092" }, topics = {
        "game-ended-topic" })
public class GameKafkaPipelineTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @DisplayName("IT-03: Publish mock GameEndedEvent to embedded Kafka topic and verify consumer processing")
    void testKafkaEventPublishAndConsume() {
        GameEndedEvent event = new GameEndedEvent(
                UUID.randomUUID(),
                "sahil",
                "rishabh",
                "CHECKMATE");

        // Verify that our app can serialize and push the event to the broker without
        // exceptions
        assertDoesNotThrow(() -> {
            kafkaTemplate.send("game-ended-topic", event.gameId().toString(), event).get(2, TimeUnit.SECONDS);
        }, "Kafka template should successfully send the event to the embedded broker");
    }
}