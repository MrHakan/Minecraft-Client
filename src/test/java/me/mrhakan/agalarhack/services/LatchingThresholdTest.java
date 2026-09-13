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

    /**
     * A release point the value cannot reach latches for good, which is the trap this shape invites.
     *
     * <p>Nothing here is wrong: the class does exactly what it is told. It is the caller that has to
     * pick a margin inside the range its value actually occupies, and ServerInfo did not - the tick
     * estimate is capped at 20, its lag warning asked for a full tick above a threshold that can be
     * set to 19.5, and so it warned once and never again. Kept as a test because the failure is
     * invisible from the outside: a warning that stops arriving looks exactly like a problem that
     * stopped happening.
     */
    @Test void aReleasePointAboveWhatTheValueCanReachNeverReArms() {
        double cap = 20.0;
        var unreachable = new LatchingThreshold(LatchingThreshold.Direction.BELOW, 19.5, 1.0);
        assertTrue(unreachable.update(10.0), "it alarms the first time");
        unreachable.update(cap);
        assertFalse(unreachable.update(10.0), "and never again, because 20.5 never arrives");

        var reachable = new LatchingThreshold(LatchingThreshold.Direction.BELOW, 19.5, 0.5);
        assertTrue(reachable.update(10.0));
        reachable.update(cap);
        assertTrue(reachable.update(10.0), "a release point at the cap itself does re-arm");
    }

    /** The same for a ratio that sits at 1.0 when nothing is wrong. */
    @Test void aSpikeRatioReleasesSomewhereBetweenSteadyAndAlarming() {
        double factor = 1.5;
        var tooStrict = new LatchingThreshold(LatchingThreshold.Direction.ABOVE, factor, factor / 2.0);
        assertTrue(tooStrict.update(4.0));
        tooStrict.update(1.0);
        assertFalse(tooStrict.update(4.0), "recovery wanted 0.75, a quarter below the median");

        var sane = new LatchingThreshold(LatchingThreshold.Direction.ABOVE, factor, (factor - 1.0) / 2.0);
        assertTrue(sane.update(4.0));
        sane.update(1.0);
        assertTrue(sane.update(4.0), "a steady connection clears it");
    }
}
