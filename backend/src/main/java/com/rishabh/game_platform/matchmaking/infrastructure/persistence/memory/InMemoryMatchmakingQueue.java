package com.rishabh.game_platform.matchmaking.infrastructure.persistence.memory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.stereotype.Repository;

import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.matchmaking.domain.ports.MatchmakingQueue;

@Repository

public class InMemoryMatchmakingQueue implements MatchmakingQueue {

    // A map where the Key is the GameType (e.g., CHESS) and the Value is a
    // thread-safe queue of waiting players
    private final Map<GameType, ConcurrentLinkedQueue<Player>> queues = new ConcurrentHashMap<>();

    @Override
    public void addPlayer(Player player, GameType gameType) {
        queues.computeIfAbsent(gameType, k -> new ConcurrentLinkedQueue<>()).add(player);
    }

    @Override
    public void removePlayer(UUID playerId) {
        queues.values().forEach(queue -> queue.removeIf(player -> player.getUserId().equals(playerId)));
    }

    @Override
    public Optional<Player> extractOpponent(GameType gameType, Player seekingPlayer) {
        ConcurrentLinkedQueue<Player> q = queues.get(gameType);

        if(q == null || q.isEmpty()) {
            return Optional.empty();
        }

        Player opponent = q.peek();

        if (opponent != null && !opponent.getUserId().equals(seekingPlayer.getUserId())) {
            return Optional.ofNullable(q.poll()); 
        }
        
        return Optional.empty();
    }
}
