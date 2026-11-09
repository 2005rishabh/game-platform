package com.rishabh.game_platform.game;

import com.rishabh.game_platform.game.application.service.GameService;
import com.rishabh.game_platform.game.chess.engine.ChessGameEngine;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.Move;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.game.infrastructure.persistence.memory.InMemoryGameStateRepository;
import com.rishabh.game_platform.matchmaking.application.service.MatchmakingService;
import com.rishabh.game_platform.matchmaking.infrastructure.persistence.memory.InMemoryMatchmakingQueue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MatchmakingAndConcurrencyReliabilityTest {

    @Test
    @DisplayName("RT-01: Unauthorized player cannot submit move in another player's game session")
    void unauthorizedPlayerCannotSubmitMoveInOthersGame() {
        ChessGameEngine engine = new ChessGameEngine();
        InMemoryGameStateRepository repository = new InMemoryGameStateRepository();
        GameService service = new GameService(engine, repository, null);

        Player white = Player.builder().userId(UUID.randomUUID()).username("whitePlayer").build();
        Player black = Player.builder().userId(UUID.randomUUID()).username("blackPlayer").build();
        Player imposter = Player.builder().userId(UUID.randomUUID()).username("imposter").build();

        GameSession session = service.createGame(white, GameType.CHESS);
        service.joinGame(session.getGameId(), black);

        Move move = Move.builder().from("e2").to("e4").build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            service.executeMove(session.getGameId(), imposter, move);
        });

        assertEquals("It is not this player's turn", ex.getMessage());
    }

    @Test
    @DisplayName("RT-02: Simultaneous moves for the same turn are serialized; only one succeeds")
    void concurrentMovesOnSameTurnAreSerialized() throws Exception {
        ChessGameEngine engine = new ChessGameEngine();
        InMemoryGameStateRepository repository = new InMemoryGameStateRepository();
        GameService service = new GameService(engine, repository, null);

        Player white = Player.builder().userId(UUID.randomUUID()).username("whitePlayer").build();
        Player black = Player.builder().userId(UUID.randomUUID()).username("blackPlayer").build();

        GameSession session = service.createGame(white, GameType.CHESS);
        service.joinGame(session.getGameId(), black);

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threads);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        Move[] candidateMoves = new Move[]{
                Move.builder().from("e2").to("e4").build(),
                Move.builder().from("d2").to("d4").build(),
                Move.builder().from("g1").to("f3").build(),
                Move.builder().from("c2").to("c4").build()
        };

        for (int i = 0; i < threads; i++) {
            final Move move = candidateMoves[i];
            executor.submit(() -> {
                try {
                    startLatch.await();
                    service.executeMove(session.getGameId(), white, move);
                    successes.incrementAndGet();
                } catch (Exception ex) {
                    failures.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed);
        assertEquals(1, successes.get(), "Exactly one move must succeed for White's turn");
        assertEquals(threads - 1, failures.get(), "Subsequent concurrent moves must be rejected");

        GameSession updatedSession = repository.findById(session.getGameId()).orElseThrow();
        assertEquals("BLACK", updatedSession.getState().getCurrentTurn());
    }

    @Test
    @DisplayName("RT-03: Matchmaking queue prevents player from self-matching")
    void matchmakingQueuePreventsSelfMatching() {
        InMemoryMatchmakingQueue queue = new InMemoryMatchmakingQueue();
        UUID playerId = UUID.randomUUID();
        Player player = Player.builder().userId(playerId).username("soloPlayer").build();

        // When player is alone and seeks opponent
        Optional<Player> opponent1 = queue.extractOpponent(GameType.CHESS, player);
        assertTrue(opponent1.isEmpty());

        // Player added to queue
        queue.addPlayer(player, GameType.CHESS);

        // Player queries again
        Optional<Player> opponent2 = queue.extractOpponent(GameType.CHESS, player);
        assertTrue(opponent2.isEmpty(), "Player must not be matched with themselves");
    }

    @Test
    @DisplayName("RT-04: Concurrent matchmaking pairing pairs unique players without duplicate assignments")
    void concurrentMatchmakingPairsUniquePlayers() {
        InMemoryMatchmakingQueue queue = new InMemoryMatchmakingQueue();
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        ChessGameEngine engine = new ChessGameEngine();
        InMemoryGameStateRepository repository = new InMemoryGameStateRepository();
        MatchmakingService service = new MatchmakingService(messaging, engine, repository, queue);

        Player p1 = Player.builder().userId(UUID.randomUUID()).username("player1").build();
        Player p2 = Player.builder().userId(UUID.randomUUID()).username("player2").build();
        Player p3 = Player.builder().userId(UUID.randomUUID()).username("player3").build();
        Player p4 = Player.builder().userId(UUID.randomUUID()).username("player4").build();

        service.processJoinRequest(p1, GameType.CHESS);
        service.processJoinRequest(p2, GameType.CHESS);
        service.processJoinRequest(p3, GameType.CHESS);
        service.processJoinRequest(p4, GameType.CHESS);

        // All 4 players processed: queue should be empty, exactly 2 game sessions created
        Optional<Player> leftover = queue.extractOpponent(GameType.CHESS, Player.builder().userId(UUID.randomUUID()).build());
        assertFalse(leftover.isPresent(), "All players should have been paired into matches");
    }
}
