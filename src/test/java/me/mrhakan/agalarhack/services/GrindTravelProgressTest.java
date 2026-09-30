package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrindTravelProgressTest {
    @Test void stalledCalculationOrWalkingHasABoundedPause() {
        var progress = new GrindTravelProgress();
        assertFalse(progress.stalled(0, 64, 0));
        for (int i = 1; i < GrindTravelProgress.STALL_TICKS; i++) assertFalse(progress.stalled(0, 64, 0));
        assertTrue(progress.stalled(0, 64, 0));
    }
    @Test void longTripsAndVerticalMiningTravelResetTheStallWindow() {
        var progress = new GrindTravelProgress();
        for (int i = 0; i < 10000; i++) assertFalse(progress.stalled(0, 64 + i / 100.0, 0));
        progress.reset();
        assertFalse(progress.stalled(0, 64, 0));
    }
}
