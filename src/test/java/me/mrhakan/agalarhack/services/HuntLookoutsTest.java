package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HuntLookoutsTest {
    @Test
    void twoRingsOfEightClockwiseFromEast() {
        int[][] lookouts = SurvivalTasks.HuntTask.LOOKOUTS;
        assertEquals(16, lookouts.length);
        assertArrayEquals(new int[]{48, 0, 48}, lookouts[0], "the first lookout is due east");
        assertArrayEquals(new int[]{0, 48, 48}, lookouts[2], "a quarter turn clockwise is south (+z)");
        assertArrayEquals(new int[]{-96, 0, 96}, lookouts[12]);
    }

    @Test
    void everyLookoutSitsOnItsRingAndNeighboursAreWithinSightOfEachOther() {
        int[][] lookouts = SurvivalTasks.HuntTask.LOOKOUTS;
        for (int[] lookout : lookouts) {
            double radius = Math.hypot(lookout[0], lookout[1]);
            assertEquals(lookout[2], radius, 1.0, "lookout " + lookout[0] + " " + lookout[1]);
        }
        // Consecutive lookouts on a ring are closer together than the sight range, so the walk
        // between them leaves no gap an animal could hide in along the ring.
        for (int index = 1; index < 8; index++) {
            double step = Math.hypot(lookouts[index][0] - lookouts[index - 1][0], lookouts[index][1] - lookouts[index - 1][1]);
            assertTrue(step < SurvivalTasks.HuntTask.SIGHT, "step " + index + " is " + step);
        }
    }
}
