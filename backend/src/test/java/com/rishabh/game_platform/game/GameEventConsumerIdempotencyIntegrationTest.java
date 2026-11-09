package com.rishabh.game_platform.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.rishabh.game_platform.auth.infrastructure.persistence.UserEntity;
import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import com.rishabh.game_platform.game.application.service.GameEventConsumer;
import com.rishabh.game_platform.game.domain.enums.GameStatus;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.events.GameEndedEvent;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.game.domain.ports.GameStateRepository;
import com.rishabh.game_platform.game.infrastructure.persistence.jpa.SpringDataProcessedGameEventRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class GameEventConsumerIdempotencyIntegrationTest {

    @Autowired
    private GameEventConsumer gameEventConsumer;

    @Autowired
    private SpringDataProcessedGameEventRepository processedEventRepository;

    @Autowired
    private GameStateRepository gameStateRepository;

    @MockitoSpyBean
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UserEntity createOrGetUser(String username, int initialElo) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            UserEntity user = new UserEntity();
            user.setUsername(username);
            user.setPassword("password123");
            user.setEmail(username + "@example.com");
            user.setEloRating(initialElo);
            return userRepository.save(user);
        });
    }

    private GameSession createSession(
        UUID gameId,
        String p1Name,
        String p2Name
    ) {
        GameSession session = GameSession.builder()
            .gameId(gameId)
            .gameType(GameType.CHESS)
            .status(GameStatus.IN_PROGRESS)
            .player1(Player.builder().username(p1Name).build())
            .player2(Player.builder().username(p2Name).build())
            .build();
        return gameStateRepository.save(session);
    }

    @Test
    @DisplayName(
        "IT-01: First event delivery updates both ELO ratings and records processed event"
    )
    void firstEventDeliveryUpdatesBothEloRatings() {
        UUID gameId = UUID.randomUUID();
        UserEntity winner = createOrGetUser(
            "it_w1_" + gameId.toString().substring(0, 8),
            1200
        );
        UserEntity loser = createOrGetUser(
            "it_l1_" + gameId.toString().substring(0, 8),
            1200
        );
        createSession(gameId, winner.getUsername(), loser.getUsername());

        GameEndedEvent event = new GameEndedEvent(
            gameId,
            winner.getUsername(),
            loser.getUsername(),
            "CHECKMATE"
        );

        gameEventConsumer.consumeGameEndedEvent(event);

        UserEntity updatedWinner = userRepository
            .findByUsername(winner.getUsername())
            .orElseThrow();
        UserEntity updatedLoser = userRepository
            .findByUsername(loser.getUsername())
            .orElseThrow();

        assertEquals(1216, updatedWinner.getEloRating());
        assertEquals(1184, updatedLoser.getEloRating());
        assertTrue(processedEventRepository.findById(gameId).isPresent());

        GameSession session = gameStateRepository
            .findById(gameId)
            .orElseThrow();
        assertEquals(GameStatus.WHITE_WON, session.getStatus());
    }

    @Test
    @DisplayName(
        "IT-02: Sequential duplicate delivery does not update ratings again"
    )
    void sequentialDuplicateDeliveryDoesNotUpdateRatingsAgain() {
        UUID gameId = UUID.randomUUID();
        UserEntity winner = createOrGetUser(
            "it_w2_" + gameId.toString().substring(0, 8),
            1200
        );
        UserEntity loser = createOrGetUser(
            "it_l2_" + gameId.toString().substring(0, 8),
            1200
        );
        createSession(gameId, winner.getUsername(), loser.getUsername());

        GameEndedEvent event = new GameEndedEvent(
            gameId,
            winner.getUsername(),
            loser.getUsername(),
            "CHECKMATE"
        );

        gameEventConsumer.consumeGameEndedEvent(event);
        // Duplicate delivery
        gameEventConsumer.consumeGameEndedEvent(event);

        UserEntity updatedWinner = userRepository
            .findByUsername(winner.getUsername())
            .orElseThrow();
        UserEntity updatedLoser = userRepository
            .findByUsername(loser.getUsername())
            .orElseThrow();

        assertEquals(
            1216,
            updatedWinner.getEloRating(),
            "Winner rating must not change on duplicate"
        );
        assertEquals(
            1184,
            updatedLoser.getEloRating(),
            "Loser rating must not change on duplicate"
        );
        assertTrue(processedEventRepository.findById(gameId).isPresent());
    }

    @Test
    @DisplayName(
        "IT-03: Concurrent duplicate delivery updates ratings exactly once via atomic database constraint"
    )
    void concurrentDuplicateDeliveryUpdatesRatingsExactlyOnce()
        throws Exception {
        UUID gameId = UUID.randomUUID();
        UserEntity winner = createOrGetUser(
            "it_w3_" + gameId.toString().substring(0, 8),
            1200
        );
        UserEntity loser = createOrGetUser(
            "it_l3_" + gameId.toString().substring(0, 8),
            1200
        );
        createSession(gameId, winner.getUsername(), loser.getUsername());

        GameEndedEvent event = new GameEndedEvent(
            gameId,
            winner.getUsername(),
            loser.getUsername(),
            "CHECKMATE"
        );

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    gameEventConsumer.consumeGameEndedEvent(event);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected error
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All concurrent invocations must complete");
        assertEquals(
            threadCount,
            successCount.get(),
            "No thread should fail or throw unhandled exceptions"
        );

        UserEntity updatedWinner = userRepository
            .findByUsername(winner.getUsername())
            .orElseThrow();
        UserEntity updatedLoser = userRepository
            .findByUsername(loser.getUsername())
            .orElseThrow();

        assertEquals(
            1216,
            updatedWinner.getEloRating(),
            "Winner rating should only be updated once"
        );
        assertEquals(
            1184,
            updatedLoser.getEloRating(),
            "Loser rating should only be updated once"
        );
        assertTrue(processedEventRepository.findById(gameId).isPresent());
    }

    @Test
    @DisplayName(
        "IT-04: A failed rating update rolls back the processed-event record allowing retry"
    )
    void failedRatingUpdateRollsBackProcessedEventRecord() {
        UUID gameId = UUID.randomUUID();
        String winnerName = "it_w4_" + gameId.toString().substring(0, 8);
        String loserName = "it_l4_" + gameId.toString().substring(0, 8);
        UserEntity winner = createOrGetUser(winnerName, 1200);
        UserEntity loser = createOrGetUser(loserName, 1200);
        createSession(gameId, winner.getUsername(), loser.getUsername());

        GameEndedEvent event = new GameEndedEvent(
            gameId,
            winner.getUsername(),
            loser.getUsername(),
            "CHECKMATE"
        );

        // Force userRepository.save(winner) to fail on first attempt
        doThrow(
            new RuntimeException(
                "Simulated database failure during rating update"
            )
        )
            .when(userRepository)
            .save(
                argThat(
                    u ->
                        u != null &&
                        winnerName.equals(u.getUsername()) &&
                        u.getEloRating() == 1216
                )
            );

        assertThrows(RuntimeException.class, () ->
            gameEventConsumer.consumeGameEndedEvent(event)
        );

        // The transaction must have rolled back the processed-event record!
        assertTrue(
            processedEventRepository.findById(gameId).isEmpty(),
            "Processed event record must be rolled back if rating update fails"
        );

        // Reset spy so the retry succeeds
        reset(userRepository);

        // Next delivery attempt (retry) succeeds completely
        gameEventConsumer.consumeGameEndedEvent(event);

        assertTrue(
            processedEventRepository.findById(gameId).isPresent(),
            "Processed event record must exist after successful retry"
        );
        UserEntity retriedWinner = userRepository
            .findByUsername(winnerName)
            .orElseThrow();
        assertEquals(1216, retriedWinner.getEloRating());
        UserEntity retriedLoser = userRepository
            .findByUsername(loserName)
            .orElseThrow();
        assertEquals(1184, retriedLoser.getEloRating());
    }

    @Test
    @DisplayName(
        "IT-05: Duplicate event does not leave transaction rollback-only or break subsequent event processing"
    )
    void duplicateEventDoesNotLeaveTransactionRollbackOnlyOrBreakSubsequentValidEvent() {
        UUID gameId1 = UUID.randomUUID();
        UUID gameId2 = UUID.randomUUID();

        UserEntity w1 = createOrGetUser(
            "it_w5a_" + gameId1.toString().substring(0, 8),
            1200
        );
        UserEntity l1 = createOrGetUser(
            "it_l5a_" + gameId1.toString().substring(0, 8),
            1200
        );
        createSession(gameId1, w1.getUsername(), l1.getUsername());

        UserEntity w2 = createOrGetUser(
            "it_w5b_" + gameId2.toString().substring(0, 8),
            1200
        );
        UserEntity l2 = createOrGetUser(
            "it_l5b_" + gameId2.toString().substring(0, 8),
            1200
        );
        createSession(gameId2, w2.getUsername(), l2.getUsername());

        GameEndedEvent event1 = new GameEndedEvent(
            gameId1,
            w1.getUsername(),
            l1.getUsername(),
            "CHECKMATE"
        );
        GameEndedEvent event2 = new GameEndedEvent(
            gameId2,
            w2.getUsername(),
            l2.getUsername(),
            "CHECKMATE"
        );

        // Process game 1 first
        gameEventConsumer.consumeGameEndedEvent(event1);
        assertTrue(processedEventRepository.findById(gameId1).isPresent());

        // Now run an outer transaction that receives duplicate of event 1 and then receives event 2
        TransactionTemplate txTemplate = new TransactionTemplate(
            transactionManager
        );
        txTemplate.execute(status -> {
            // Deliver duplicate of event 1
            gameEventConsumer.consumeGameEndedEvent(event1);

            // Verify status is NOT marked rollback-only
            assertFalse(
                status.isRollbackOnly(),
                "Duplicate event must NOT mark transaction rollback-only"
            );

            // Deliver event 2 in the same active transaction
            gameEventConsumer.consumeGameEndedEvent(event2);

            assertFalse(
                status.isRollbackOnly(),
                "Transaction should remain healthy after processing event 2"
            );
            return null;
        });

        // Verify both games are fully processed
        assertTrue(processedEventRepository.findById(gameId1).isPresent());
        assertTrue(processedEventRepository.findById(gameId2).isPresent());

        UserEntity updatedW2 = userRepository
            .findByUsername(w2.getUsername())
            .orElseThrow();
        assertEquals(1216, updatedW2.getEloRating());
    }

    @Test
    @DisplayName(
        "IT-06: Two different game IDs can be processed independently without interference"
    )
    void twoDifferentGameIdsCanBeProcessedIndependently() {
        UUID gameIdA = UUID.randomUUID();
        UUID gameIdB = UUID.randomUUID();

        UserEntity userA1 = createOrGetUser(
            "it_u6a1_" + gameIdA.toString().substring(0, 8),
            1200
        );
        UserEntity userA2 = createOrGetUser(
            "it_u6a2_" + gameIdA.toString().substring(0, 8),
            1200
        );
        createSession(gameIdA, userA1.getUsername(), userA2.getUsername());

        UserEntity userB1 = createOrGetUser(
            "it_u6b1_" + gameIdB.toString().substring(0, 8),
            1200
        );
        UserEntity userB2 = createOrGetUser(
            "it_u6b2_" + gameIdB.toString().substring(0, 8),
            1200
        );
        createSession(gameIdB, userB1.getUsername(), userB2.getUsername());

        GameEndedEvent eventA = new GameEndedEvent(
            gameIdA,
            userA1.getUsername(),
            userA2.getUsername(),
            "CHECKMATE"
        );
        GameEndedEvent eventB = new GameEndedEvent(
            gameIdB,
            userB1.getUsername(),
            userB2.getUsername(),
            "CHECKMATE"
        );

        gameEventConsumer.consumeGameEndedEvent(eventA);
        gameEventConsumer.consumeGameEndedEvent(eventB);

        assertTrue(processedEventRepository.findById(gameIdA).isPresent());
        assertTrue(processedEventRepository.findById(gameIdB).isPresent());

        UserEntity finalA1 = userRepository
            .findByUsername(userA1.getUsername())
            .orElseThrow();
        UserEntity finalA2 = userRepository
            .findByUsername(userA2.getUsername())
            .orElseThrow();
        UserEntity finalB1 = userRepository
            .findByUsername(userB1.getUsername())
            .orElseThrow();
        UserEntity finalB2 = userRepository
            .findByUsername(userB2.getUsername())
            .orElseThrow();

        assertEquals(1216, finalA1.getEloRating());
        assertEquals(1184, finalA2.getEloRating());
        assertEquals(1216, finalB1.getEloRating());
        assertEquals(1184, finalB2.getEloRating());
    }
}
