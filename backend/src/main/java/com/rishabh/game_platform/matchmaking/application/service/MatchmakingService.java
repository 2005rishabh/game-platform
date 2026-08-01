package com.rishabh.game_platform.matchmaking.application.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.rishabh.game_platform.game.application.service.GameService;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.matchmaking.domain.ports.MatchmakingQueue;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MatchmakingService {
    private final MatchmakingQueue matchmakingQueue;
    private final GameService gameService;

    public Optional<GameSession> joinMatchmaking(Player player, GameType gameType) {

        // 1. Check if anyone else is already waiting to play this game type
        Optional<Player> opponentOpt = matchmakingQueue.extractOpponent(gameType, player);

        if (opponentOpt.isPresent()) {
            // 2. Found someone so create a new game with the waiting player as the host
            Player host = opponentOpt.get();
            GameSession newSession = gameService.createGame(host, gameType);

            // 3. Add our current player to the newly created game
            GameSession startedSession = gameService.joinGame(newSession.getGameId(), player);
            return Optional.of(startedSession);
        } else {
            // 4. Nobody is waiting. Put this player in the queue.
            return Optional.empty();
        }
    }

    public void leaveMatchmaking(UUID playerId) {
        matchmakingQueue.removePlayer(playerId);
    }

}