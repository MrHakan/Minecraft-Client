package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.mrhakan.agalarhack.services.LatchingThreshold.Direction;
import org.junit.jupiter.api.Test;

class LatchingThresholdTest {

    @Test
    void firesOnceOnTheWayDown() {
        var threshold = new LatchingThreshold(Direction.BELOW, 15.0, 1.0);
        assertFalse(threshold.update(20.0));
        assertTrue(threshold.update(14.0), "first crossing must report");
        assertFalse(threshold.update(13.0), "still bad is not news");
        assertFalse(threshold.update(14.9));
    }

    @Test
    void hoveringAtTheThresholdCannotProduceAWallOfWarnings() {
        var threshold = new LatchingThreshold(Direction.BELOW, 15.0, 1.0);
        assertTrue(threshold.update(14.99));
        int fired = 0;
        for (int tick = 0; tick < 100; tick++) {
            // Drifting either side of the line by a rounding error, which is the real failure case.
            if (threshold.update(tick % 2 == 0 ? 15.01 : 14.99)) fired++;
        }
        assertFalse(fired > 0, "fired " + fired + " extra times while hovering");
    }

    @Test
    void reArmsOnlyAfterARealRecovery() {
        var threshold = new LatchingThreshold(Direction.BELOW, 15.0, 1.0);
        assertTrue(threshold.update(14.0));
        threshold.update(15.5);
        assertTrue(threshold.isLatched(), "15.5 is above the line but inside the margin");
        threshold.update(16.0);
        assertFalse(threshold.isLatched());
        assertTrue(threshold.update(14.0), "a genuine second episode must report");
    }

    @Test
    void firesOnceOnTheWayUp() {
        var threshold = new LatchingThreshold(Direction.ABOVE, 250.0, 50.0);
        assertFalse(threshold.update(100.0));
        assertTrue(threshold.update(400.0));
        assertFalse(threshold.update(900.0));
        threshold.update(220.0);
        assertTrue(threshold.isLatched(), "inside the margin");
        threshold.update(200.0);
        assertFalse(threshold.isLatched());
        assertTrue(threshold.update(300.0));
    }

    @Test
    void exactlyAtTheThresholdIsNotAlarming() {
        assertFalse(new LatchingThreshold(Direction.BELOW, 15.0, 1.0).update(15.0));
        assertFalse(new LatchingThreshold(Direction.ABOVE, 250.0, 50.0).update(250.0));
    }

    @Test
    void aZeroMarginStillWorksAndReArmsAtTheLine() {
        var threshold = new LatchingThreshold(Direction.BELOW, 15.0, 0.0);
        assertTrue(threshold.update(14.0));
        threshold.update(15.0);
        assertFalse(threshold.isLatched());
    }

    @Test
    void aNegativeMarginIsTreatedAsZeroRatherThanInvertingTheLogic() {
        var threshold = new LatchingThreshold(Direction.BELOW, 15.0, -5.0);
        assertTrue(threshold.update(14.0));
        threshold.update(15.0);
        assertFalse(threshold.isLatched());
    }

    @Test
    void nonFiniteValuesAreIgnoredRatherThanLatching() {
        var threshold = new LatchingThreshold(Direction.BELOW, 15.0, 1.0);
        assertFalse(threshold.update(Double.NaN));
        assertFalse(threshold.update(Double.NEGATIVE_INFINITY));
        assertFalse(threshold.isLatched());
        assertTrue(threshold.update(14.0), "a real sample after the noise must still report");
    }

    @Test
    void resetClearsTheLatchSilently() {
        var threshold = new LatchingThreshold(Direction.BELOW, 15.0, 1.0);
        assertTrue(threshold.update(14.0));
        threshold.reset();
        assertFalse(threshold.isLatched());
        assertTrue(threshold.update(14.0));
    }
}
