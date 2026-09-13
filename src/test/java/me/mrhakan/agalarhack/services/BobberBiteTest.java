package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BobberBiteTest {

    private static BobberBite.Detector detector() {
        return new BobberBite.Detector(BobberBite.DEFAULT_THRESHOLD, 20);
    }

    @Test
    void aSharpPlungeInWaterIsABite() {
        assertTrue(detector().update(0, true, -0.4));
    }

    @Test
    void gentleBobbingIsNot() {
        BobberBite.Detector detector = detector();
        for (int tick = 0; tick < 100; tick++) {
            assertFalse(detector.update(tick, true, tick % 2 == 0 ? -0.01 : 0.01), "tick " + tick);
        }
    }

    @Test
    void fallingThroughTheAirIsNotABite() {
        // The bobber is still flying to where it will land; gravity is not a fish.
        assertFalse(detector().update(0, false, -0.9));
    }

    @Test
    void upwardMotionIsNeverABite() {
        assertFalse(detector().update(0, true, 0.5));
    }

    @Test
    void onePlungeIsReportedOnceNotEveryTickOfIt() {
        BobberBite.Detector detector = detector();
        assertTrue(detector.update(0, true, -0.4));
        int extra = 0;
        // A plunge lasts several ticks; without the gap the module reels the empty line straight back.
        for (int tick = 1; tick < 20; tick++) {
            if (detector.update(tick, true, -0.4)) extra++;
        }
        assertFalse(extra > 0, "reported " + extra + " extra times during one plunge");
    }

    @Test
    void aSecondBiteAfterTheGapIsReported() {
        BobberBite.Detector detector = detector();
        assertTrue(detector.update(0, true, -0.4));
        assertTrue(detector.update(20, true, -0.4));
    }

    @Test
    void exactlyAtTheThresholdCounts() {
        BobberBite.Detector detector = new BobberBite.Detector(0.08, 20);
        assertTrue(detector.update(0, true, -0.08));
    }

    @Test
    void justUnderTheThresholdDoesNot() {
        assertFalse(new BobberBite.Detector(0.08, 20).update(0, true, -0.079));
    }

    @Test
    void nonFiniteMotionIsIgnoredRatherThanTriggering() {
        BobberBite.Detector detector = detector();
        assertFalse(detector.update(0, true, Double.NaN));
        assertFalse(detector.update(1, true, Double.NEGATIVE_INFINITY));
        assertTrue(detector.update(2, true, -0.4), "and a real plunge after the noise still reports");
    }

    @Test
    void resetLetsTheNextCastTriggerImmediately() {
        BobberBite.Detector detector = detector();
        assertTrue(detector.update(0, true, -0.4));
        detector.reset();
        assertTrue(detector.update(1, true, -0.4));
    }

    @Test
    void degenerateSettingsCannotDisableOrSpamIt() {
        BobberBite.Detector zeroGap = new BobberBite.Detector(0.0, 0);
        assertTrue(zeroGap.update(0, true, -0.4));
        assertFalse(zeroGap.update(0, true, -0.4), "a zero gap must still mean one report per tick at most");
        assertTrue(zeroGap.update(1, true, -0.4));
    }
}
