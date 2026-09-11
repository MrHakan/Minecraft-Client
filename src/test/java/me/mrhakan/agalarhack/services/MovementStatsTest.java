package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MovementStatsTest {
    private static final double EPSILON = 1e-9;

    /** Walks in a straight line at a fixed per-tick distance, starting from the origin. */
    private static MovementStats walking(double blocksPerTick, int ticks) {
        MovementStats stats = new MovementStats();
        for (int tick = 0; tick <= ticks; tick++) stats.sample(blocksPerTick * tick, 64, 0);
        return stats;
    }

    @Test
    void theFirstSampleOnlyEstablishesAStartingPoint() {
        MovementStats stats = new MovementStats();
        stats.sample(0, 64, 0);
        assertFalse(stats.hasSamples(), "one position is not a movement");
        assertEquals(0, stats.horizontalSpeed(), EPSILON);
    }

    @Test
    void convertsPerTickDeltasToBlocksPerSecond() {
        MovementStats stats = walking(0.2, 5);
        assertEquals(4.0, stats.horizontalSpeed(), EPSILON, "0.2 b/t at 20 tps is 4 b/s");
        assertEquals(4.0, stats.averageHorizontalSpeed(), EPSILON);
        assertEquals(4.0, stats.peakHorizontalSpeed(), EPSILON);
    }

    @Test
    void diagonalMovementIsMeasuredAsDistanceNotAsAnAxis() {
        MovementStats stats = new MovementStats();
        stats.sample(0, 64, 0);
        stats.sample(0.3, 64, 0.4);
        assertEquals(0.5 * MovementStats.TICKS_PER_SECOND, stats.horizontalSpeed(), EPSILON);
    }

    @Test
    void verticalSpeedIsSignedSoFallingReadsNegative() {
        MovementStats stats = new MovementStats();
        stats.sample(0, 64, 0);
        stats.sample(0, 63.6, 0);
        assertEquals(-8.0, stats.verticalSpeed(), 1e-6);
        stats.sample(0, 64.1, 0);
        assertEquals(10.0, stats.verticalSpeed(), 1e-6, "63.6 to 64.1 is +0.5 a tick, so +10 b/s");
    }

    @Test
    void heightChangeDoesNotInflateHorizontalSpeed() {
        MovementStats stats = new MovementStats();
        stats.sample(0, 64, 0);
        stats.sample(0, 50, 0);
        assertEquals(0, stats.horizontalSpeed(), EPSILON, "falling straight down is not moving");
    }

    @Test
    void peakFallSpeedIsPositiveAndZeroWhenNothingFell() {
        MovementStats climbing = new MovementStats();
        climbing.sample(0, 64, 0);
        climbing.sample(0, 65, 0);
        assertEquals(0, climbing.peakFallSpeed(), EPSILON);

        MovementStats falling = new MovementStats();
        falling.sample(0, 64, 0);
        falling.sample(0, 63, 0);
        falling.sample(0, 61, 0);
        falling.sample(0, 60.5, 0);
        assertEquals(2.0 * MovementStats.TICKS_PER_SECOND, falling.peakFallSpeed(), EPSILON);
    }

    @Test
    void accelerationIsTheChangeSinceTheLastTick() {
        MovementStats stats = new MovementStats();
        stats.sample(0, 64, 0);
        stats.sample(0.1, 64, 0);
        stats.sample(0.3, 64, 0);
        double expected = 0.1 * MovementStats.TICKS_PER_SECOND * MovementStats.TICKS_PER_SECOND;
        assertEquals(expected, stats.horizontalAcceleration(), 1e-6);
    }

    @Test
    void steadySpeedMeansZeroAcceleration() {
        assertEquals(0, walking(0.25, 10).horizontalAcceleration(), 1e-6);
    }

    @Test
    void slowingDownReadsAsNegativeAcceleration() {
        MovementStats stats = new MovementStats();
        stats.sample(0, 64, 0);
        stats.sample(0.4, 64, 0);
        stats.sample(0.5, 64, 0);
        assertTrue(stats.horizontalAcceleration() < 0);
    }

    @Test
    void aTeleportIsDiscardedRatherThanReportedAsSpeed() {
        MovementStats stats = walking(0.2, 10);
        stats.sample(100_000, 64, 0);
        assertFalse(stats.hasSamples(), "the window must be cleared, not left mixing two situations");
        assertEquals(0, stats.horizontalSpeed(), EPSILON);

        // And the next real tick measures from the new position, not from where the player was.
        stats.sample(100_000.2, 64, 0);
        assertEquals(4.0, stats.horizontalSpeed(), EPSILON);
    }

    @Test
    void aVerticalTeleportIsDiscardedToo() {
        MovementStats stats = walking(0.2, 10);
        stats.sample(2.0, 5_000, 0);
        assertFalse(stats.hasSamples());
    }

    @Test
    void averageAndPeakDifferWhenSpeedVaries() {
        MovementStats stats = new MovementStats();
        stats.sample(0, 64, 0);
        stats.sample(0.1, 64, 0);
        stats.sample(0.4, 64, 0);
        assertEquals(0.2 * MovementStats.TICKS_PER_SECOND, stats.averageHorizontalSpeed(), 1e-6);
        assertEquals(0.3 * MovementStats.TICKS_PER_SECOND, stats.peakHorizontalSpeed(), 1e-6);
    }

    @Test
    void windowIsBoundedAndOldTicksAgeOut() {
        MovementStats stats = new MovementStats();
        stats.sample(0, 64, 0);
        stats.sample(1.0, 64, 0);
        double position = 1.0;
        for (int tick = 0; tick < MovementStats.WINDOW; tick++) {
            position += 0.1;
            stats.sample(position, 64, 0);
        }
        assertEquals(MovementStats.WINDOW, stats.history().length);
        assertEquals(0.1 * MovementStats.TICKS_PER_SECOND, stats.peakHorizontalSpeed(), 1e-6,
                "the one-block tick must have aged out");
    }

    @Test
    void resetClearsEverythingIncludingAcceleration() {
        MovementStats stats = new MovementStats();
        stats.sample(0, 64, 0);
        stats.sample(0.5, 64, 0);
        stats.reset();
        assertFalse(stats.hasSamples());
        assertEquals(0, stats.horizontalAcceleration(), EPSILON);
        assertEquals(0, stats.history().length);
        // The first sample after a reset must establish a start, not measure from the old position.
        stats.sample(90, 64, 0);
        assertFalse(stats.hasSamples());
    }

    @Test
    void historyIsOldestFirstAndInBlocksPerSecond() {
        MovementStats stats = new MovementStats();
        stats.sample(0, 64, 0);
        stats.sample(0.1, 64, 0);
        stats.sample(0.4, 64, 0);
        double[] history = stats.history();
        assertEquals(2, history.length);
        assertEquals(0.1 * MovementStats.TICKS_PER_SECOND, history[0], 1e-6);
        assertEquals(0.3 * MovementStats.TICKS_PER_SECOND, history[1], 1e-6);
    }
}
