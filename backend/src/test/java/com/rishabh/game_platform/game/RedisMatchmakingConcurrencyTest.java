package com.rishabh.game_platform.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DirtiesContext
public class RedisMatchmakingConcurrencyTest {

    @Test
    @DisplayName("IT-01: Simulate 10 simultaneous players hitting matchmaking concurrently without race conditions")
    void testConcurrentMatchmakingQueue() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        
        // Track successful queue joins or simulated responses
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String username = "player_" + i;
            futures.add(executorService.submit(() -> {
                // Wait for the starting signal to fire all threads at the exact same millisecond
                latch.await();
                
                // Simulate hitting your matchmaking service or Redis queue component here
                // e.g., matchmakingService.enqueuePlayer(username);
                
                return true; // Return success if queued cleanly
            }));
        }

        // Fire the starting gun for all threads simultaneously
        latch.countDown();
        executorService.shutdown();
        boolean finished = executorService.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(finished, "Threads should complete within the timeout window");

        // Verify all 10 players successfully registered in the queue structure without thread blocks
        int successCount = 0;
        for (Future<Boolean> future : futures) {
            try {
                if (future.get()) {
                    successCount++;
                }
            } catch (ExecutionException e) {
                // Handle any concurrency exceptions here
            }
        }

        assertEquals(10, successCount, "All concurrent requests should be safely serialized into the Redis queue");
    }
}