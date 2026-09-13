package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CriticalHitsTest {

    /** Falling, in the air, not sprinting: the textbook crit. */
    private static CriticalHits.State falling() {
        return new CriticalHits.State(1.5, false, false, false, false, false, false);
    }

    @Test
    void fallingAndNotSprintingIsACrit() {
        assertTrue(CriticalHits.wouldCrit(falling()));
    }

    @Test
    void everySingleTermCanVetoIt() {
        assertFalse(CriticalHits.wouldCrit(new CriticalHits.State(0.0, false, false, false, false, false, false)),
                "no fall distance");
        assertFalse(CriticalHits.wouldCrit(new CriticalHits.State(1.5, true, false, false, false, false, false)),
                "on the ground");
        assertFalse(CriticalHits.wouldCrit(new CriticalHits.State(1.5, false, true, false, false, false, false)),
                "on a ladder");
        assertFalse(CriticalHits.wouldCrit(new CriticalHits.State(1.5, false, false, true, false, false, false)),
                "in water");
        assertFalse(CriticalHits.wouldCrit(new CriticalHits.State(1.5, false, false, false, true, false, false)),
                "mobility restricted");
        assertFalse(CriticalHits.wouldCrit(new CriticalHits.State(1.5, false, false, false, false, true, false)),
                "riding something");
        assertFalse(CriticalHits.wouldCrit(new CriticalHits.State(1.5, false, false, false, false, false, true)),
                "sprinting");
    }

    @Test
    void aNegativeFallDistanceIsNotACrit() {
        assertFalse(CriticalHits.wouldCrit(new CriticalHits.State(-1.0, false, false, false, false, false, false)));
    }

    @Test
    void nullStateIsNotACrit() {
        assertFalse(CriticalHits.wouldCrit(null));
    }

    @Test
    void theSmallestPositiveFallStillCounts() {
        // The game tests > 0, not a threshold, so the very first tick off the ground qualifies.
        assertTrue(CriticalHits.wouldCrit(new CriticalHits.State(Double.MIN_VALUE, false, false, false, false, false, false)));
    }
}
