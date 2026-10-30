package com.rishabh.game_platform.game.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EloCalculationTest {

    private static final int K_FACTOR = 32;

    /**
     * Replicates the ELO rating formula used in our event consumer 
     * to test the mathematical behavior in complete isolation.
     */
    private int[] calculateElo(int winnerRating, int loserRating, boolean isDraw) {
        double expectedWinner = 1.0 / (1.0 + Math.pow(10, (loserRating - winnerRating) / 400.0));
        double expectedLoser = 1.0 / (1.0 + Math.pow(10, (winnerRating - loserRating) / 400.0));

        double actualWinner = isDraw ? 0.5 : 1.0;
        double actualLoser = isDraw ? 0.5 : 0.0;

        int newWinnerRating = (int) Math.round(winnerRating + K_FACTOR * (actualWinner - expectedWinner));
        int newLoserRating = (int) Math.round(loserRating + K_FACTOR * (actualLoser - expectedLoser));

        return new int[]{newWinnerRating, newLoserRating};
    }

    @Test
    @DisplayName("UT-01-A: Higher-rated player beats lower-rated player (Expected Outcome)")
    void testFavoriteWins() {
        int winnerRating = 1600; // Favorite
        int loserRating = 1400;  // Underdog

        int[] newRatings = calculateElo(winnerRating, loserRating, false);

        int newWinner = newRatings[0];
        int newLoser = newRatings[1];

        // The favorite should win, but gain a small amount of points (+8)
        assertTrue(newWinner > winnerRating, "Winner rating should increase");
        assertTrue(newLoser < loserRating, "Loser rating should decrease");
        assertEquals(1608, newWinner);
        assertEquals(1392, newLoser);
    }

    @Test
    @DisplayName("UT-01-B: Underdog upsets higher-rated player (Upset Outcome)")
    void testUnderdogUpsets() {
        int winnerRating = 1400; // Underdog
        int loserRating = 1600;  // Favorite

        int[] newRatings = calculateElo(winnerRating, loserRating, false);

        int newWinner = newRatings[0];
        int newLoser = newRatings[1];

        // The underdog pulled off an upset, so they should gain a large amount of points (+24)
        assertTrue(newWinner > winnerRating, "Underdog winner rating should increase significantly");
        assertTrue(newLoser < loserRating, "Favorite loser rating should decrease significantly");
        assertEquals(1424, newWinner);
        assertEquals(1576, newLoser);
    }

    @Test
    @DisplayName("UT-01-C: Draw between players of equal rating")
    void testDrawEqualRatings() {
        int player1Rating = 1500;
        int player2Rating = 1500;

        int[] newRatings = calculateElo(player1Rating, player2Rating, true);

        // Equal ratings in a draw result in 0 net change for both players
        assertEquals(1500, newRatings[0]);
        assertEquals(1500, newRatings[1]);
    }
}