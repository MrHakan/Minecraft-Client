package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScreenTransitionTest {
    @Test void progressRunsFromNothingToFinished() {
        assertEquals(0, ScreenTransition.progress(0, 200));
        assertEquals(0.5, ScreenTransition.progress(100, 200));
        assertEquals(1, ScreenTransition.progress(200, 200));
        assertEquals(1, ScreenTransition.progress(10_000, 200));
    }

    /** A clock that went backwards must not produce a transition that runs in reverse. */
    @Test void timeBeforeTheStartIsTheStart() {
        assertEquals(0, ScreenTransition.progress(-5000, 200));
    }

    /**
     * Reduced motion is the same path with no duration, not a branch that skips the drawing. A
     * duration of zero has to finish on the first frame or a themed client would sit on a
     * half-drawn transition forever.
     */
    @Test void noDurationIsAlreadyFinished() {
        assertEquals(1, ScreenTransition.progress(0, 0));
        assertEquals(1, ScreenTransition.progress(-1, -1));
    }

    @Test void motionOffMeansNoDuration() {
        assertEquals(0, ScreenTransition.duration(false, 1));
        assertEquals(0, ScreenTransition.duration(false, 4));
    }

    @Test void aFasterThemeGetsAShorterTransition() {
        long normal = ScreenTransition.duration(true, 1);
        assertEquals(ScreenTransition.DEFAULT_MILLIS, normal);
        assertTrue(ScreenTransition.duration(true, 2) < normal);
        assertTrue(ScreenTransition.duration(true, 0.5) > normal);
    }

    /** A hand-edited theme can hold anything; the duration must stay usable. */
    @Test void nonsenseSpeedStillGivesARealDuration() {
        assertTrue(ScreenTransition.duration(true, Double.NaN) > 0);
        assertTrue(ScreenTransition.duration(true, 0) > 0);
        assertTrue(ScreenTransition.duration(true, 1e9) > 0);
    }

    @Test void easingStartsFastAndArrivesGently() {
        assertEquals(0, ScreenTransition.ease(0));
        assertEquals(1, ScreenTransition.ease(1));
        assertTrue(ScreenTransition.ease(0.25) > 0.25, "ease-out is ahead of linear early on");
        assertTrue(ScreenTransition.ease(0.9) > 0.9);
        assertTrue(ScreenTransition.ease(0.5) < 1);
    }

    @Test void easingIsClampedAndNeverNaN() {
        assertEquals(0, ScreenTransition.ease(-3));
        assertEquals(1, ScreenTransition.ease(4));
        assertEquals(1, ScreenTransition.ease(Double.NaN));
    }

    @Test void interpolationLandsOnBothEnds() {
        assertEquals(10, ScreenTransition.between(10, 90, 0));
        assertEquals(90, ScreenTransition.between(10, 90, 1));
        double middle = ScreenTransition.between(10, 90, 0.5);
        assertTrue(middle > 10 && middle < 90);
    }
}
