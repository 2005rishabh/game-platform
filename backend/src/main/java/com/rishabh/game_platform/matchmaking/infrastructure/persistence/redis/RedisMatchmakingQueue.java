package com.rishabh.game_platform.matchmaking.infrastructure.persistence.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.matchmaking.domain.ports.MatchmakingQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@Primary // Tells Spring Boot to use THIS implementation instead of the InMemory one
@RequiredArgsConstructor
public class RedisMatchmakingQueue implements MatchmakingQueue {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper; // Spring Boot automatically provides this for JSON

    private static final String PLAYER_HASH_KEY = "matchmaking:players";

    private String getQueueKey(GameType gameType) {
        return "matchmaking:queue:" + gameType.name();
    }

    @Override
    public void addPlayer(Player player, GameType gameType) {
        try {
            String playerIdStr = player.getUserId().toString();
            String playerJson = objectMapper.writeValueAsString(player);

            // 1. Store the full player JSON in a fast-lookup Redis Hash
            redisTemplate.opsForHash().put(PLAYER_HASH_KEY, playerIdStr, playerJson);

            // 2. Push just their ID onto the right side of the queue
            redisTemplate.opsForList().rightPush(getQueueKey(gameType), playerIdStr);

            log.info("Added player {} to Redis queue {}", player.getUsername(), gameType);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize player for Redis", e);
            throw new RuntimeException("Failed to serialize player", e);
        }
    }

    @Override
    public void removePlayer(UUID playerId) {
        // LAZY DELETION: just delete them from the Hash.
        // When they eventually pop out of the queue, the system will
        // see they are gone and ignore them.
        redisTemplate.opsForHash().delete(PLAYER_HASH_KEY, playerId.toString());
        log.info("Removed player {} from Redis matchmaking hash (Lazy Deletion)", playerId);
    }

    @Override
    public Optional<Player> extractOpponent(GameType gameType, Player seekingPlayer) {
        String queueKey = getQueueKey(gameType);
        String seekingPlayerIdStr = seekingPlayer.getUserId().toString();

        // Loop handles "ghost" players who canceled (lazy deletion)
        while (true) {
            // Pop the first person waiting in line
            String potentialOpponentId = redisTemplate.opsForList().leftPop(queueKey);

            if (potentialOpponentId == null) {
                return Optional.empty(); // No one is waiting
            }

            // Edge case guard: Don't match a player against themselves
            if (potentialOpponentId.equals(seekingPlayerIdStr)) {
                continue;
            }

            // Look up their actual data in the Hash
            Object opponentJsonObj = redisTemplate.opsForHash().get(PLAYER_HASH_KEY, potentialOpponentId);

            if (opponentJsonObj != null) {
                try {
                    // Rebuild the Player object from JSON
                    Player opponent = objectMapper.readValue((String) opponentJsonObj, Player.class);

                    // Cleanup: Remove them from the Hash since they are leaving the queue to play
                    redisTemplate.opsForHash().delete(PLAYER_HASH_KEY, potentialOpponentId);

                    log.info("Match found! Opponent {} extracted from Redis", opponent.getUsername());
                    return Optional.of(opponent);
                } catch (JsonProcessingException e) {
                    log.error("Failed to deserialize opponent from Redis", e);
                }
            }
            // If opponentJsonObj is null, they canceled!
            // The while(true) loop continues to the next person.
        }
    }
}