package com.rishabh.game_platform.matchmaking.domain.ports;

import java.util.Optional;
import java.util.UUID;

import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.model.Player;

public interface MatchmakingQueue {
    void addPlayer(Player player, GameType gameType);

    void removePlayer(UUID playerId);

    Optional<Player> extractOpponent(GameType gameType, Player seekingPlayer);
}
