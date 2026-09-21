package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RainbowColorsTest {
    private static final int MAGENTA = 0xFF00FF;
    private static final int MUTED = 0x806080;

    private static int cycle(long millis) {
        return RainbowColors.cycle(millis, 4.0, MAGENTA, 0.0);
    }

    @Test
    void theColourChangesOverTime() {
        assertNotEquals(cycle(0), cycle(1000));
    }

    @Test
    void oneFullPeriodReturnsToTheStart() {
        assertEquals(cycle(0), cycle(4000));
        assertEquals(cycle(1234), cycle(1234 + 4000));
    }

    @Test
    void speedIsRespected() {
        // Half the period means twice as far round the wheel in the same wall-clock time.
        assertEquals(RainbowColors.cycle(1000, 2.0, MAGENTA, 0.0),
                RainbowColors.cycle(2000, 4.0, MAGENTA, 0.0));
    }

    @Test
    void phaseOffsetsAlongTheSameCycle() {
        assertEquals(RainbowColors.cycle(0, 4.0, MAGENTA, 0.25),
                RainbowColors.cycle(1000, 4.0, MAGENTA, 0.0),
                "a quarter of a four second cycle is one second along it");
    }

    @Test
    void aWholePhaseIsNoPhase() {
        assertEquals(RainbowColors.cycle(500, 4.0, MAGENTA, 0.0),
                RainbowColors.cycle(500, 4.0, MAGENTA, 1.0));
        assertEquals(RainbowColors.cycle(500, 4.0, MAGENTA, 0.25),
                RainbowColors.cycle(500, 4.0, MAGENTA, -0.75),
                "a negative phase must wrap rather than fall off the wheel");
    }

    @Test
    void aNegativeClockStaysOnTheCycle() {
        int colour = RainbowColors.cycle(-1500, 4.0, MAGENTA, 0.0);
        assertTrue(colour >= 0 && colour <= 0xFFFFFF, Integer.toHexString(colour));
        assertEquals(RainbowColors.cycle(-1500, 4.0, MAGENTA, 0.0),
                RainbowColors.cycle(-1500 + 4000, 4.0, MAGENTA, 0.0));
    }

    @Test
    void anAbsurdPeriodCannotDivideByZero() {
        int colour = RainbowColors.cycle(1000, 0.0, MAGENTA, 0.0);
        assertTrue(colour >= 0 && colour <= 0xFFFFFF);
    }

    @Test
    void everyResultStaysInsideTwentyFourBits() {
        for (long millis = 0; millis < 4000; millis += 37) {
            int colour = cycle(millis);
            assertTrue(colour >= 0 && colour <= 0xFFFFFF, millis + " gave " + Integer.toHexString(colour));
        }
    }

    @Test
    void aMutedBaseStaysMutedWhileItCycles() {
        float[] base = RainbowColors.toHsb(MUTED);
        for (long millis = 0; millis < 4000; millis += 250) {
            float[] cycled = RainbowColors.toHsb(RainbowColors.cycle(millis, 4.0, MUTED, 0.0));
            assertEquals(base[1], cycled[1], 0.02f, "saturation drifted at " + millis);
            assertEquals(base[2], cycled[2], 0.02f, "brightness drifted at " + millis);
        }
    }

    @Test
    void aGreyBaseCyclesVisiblyRatherThanStayingGrey() {
        // Grey has no hue to preserve, so cycling it literally would look broken.
        int first = RainbowColors.cycle(0, 4.0, 0x808080, 0.0);
        int later = RainbowColors.cycle(1000, 4.0, 0x808080, 0.0);
        assertNotEquals(first, later);
        assertTrue(RainbowColors.toHsb(first)[1] > 0.5f, "should be saturated, was " + Integer.toHexString(first));
    }

    @Test
    void aBlackBaseIsNotInvisible() {
        int colour = RainbowColors.cycle(0, 4.0, 0x000000, 0.0);
        assertTrue(RainbowColors.toHsb(colour)[2] > 0.5f, "was " + Integer.toHexString(colour));
    }

    @Test
    void theCycleActuallyCoversTheWheel() {
        java.util.Set<Integer> hues = new java.util.HashSet<>();
        for (long millis = 0; millis < 4000; millis += 100) {
            hues.add(Math.round(RainbowColors.toHsb(cycle(millis))[0] * 12));
        }
        assertTrue(hues.size() >= 10, "only reached " + hues.size() + " of twelve hue buckets");
    }
}
