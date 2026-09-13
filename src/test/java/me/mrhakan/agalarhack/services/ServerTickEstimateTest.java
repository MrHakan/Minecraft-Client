package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerTickEstimateTest {
    @Test void oneUpdateIsNotYetAnEstimate() {
        var estimate = new ServerTickEstimate(16);
        estimate.update(1000, 20);
        assertFalse(estimate.hasEstimate());
        assertEquals(0, estimate.average());
    }

    @Test void aHealthyServerReadsAsTheNominalRate() {
        var estimate = new ServerTickEstimate(16);
        // 20 ticks per 1000 ms is 50 ms per tick.
        for (int i = 0; i <= 5; i++) estimate.update(i * 1000L, 20);
        assertEquals(20.0, estimate.average(), 1e-6);
    }

    @Test void aSlowServerReadsBelowNominal() {
        var estimate = new ServerTickEstimate(16);
        // 20 ticks per 2000 ms is 100 ms per tick, so 10 TPS.
        for (int i = 0; i <= 5; i++) estimate.update(i * 2000L, 20);
        assertEquals(10.0, estimate.average(), 1e-6);
    }

    @Test void theEstimateIsCappedAtTheNominalRate() {
        var estimate = new ServerTickEstimate(16);
        // Faster than 20 TPS is noise, not a fast server.
        for (int i = 0; i <= 5; i++) estimate.update(i * 100L, 20);
        assertEquals(ServerTickEstimate.NOMINAL_TPS, estimate.average(), 1e-9);
        assertTrue(estimate.current() <= ServerTickEstimate.NOMINAL_TPS);
    }

    @Test void minimumIsTheSlowestObservedRate() {
        var estimate = new ServerTickEstimate(16);
        estimate.update(0, 20);
        estimate.update(1000, 20);
        estimate.update(3000, 20);
        assertEquals(10.0, estimate.minimum(), 1e-6);
        assertEquals(20.0, estimate.maximum(), 1e-6);
    }

    @Test void pausesAndClockProblemsAreDiscardedNotReportedAsLag() {
        var estimate = new ServerTickEstimate(16);
        estimate.update(0, 20);
        estimate.update(600_000, 20);
        assertFalse(estimate.hasEstimate(), "a multi-minute gap is a pause, not a slow server");

        var backwards = new ServerTickEstimate(16);
        backwards.update(5000, 20);
        backwards.update(1000, 20);
        assertFalse(backwards.hasEstimate());
    }

    @Test void nonPositiveTickAdvancesAreIgnored() {
        var estimate = new ServerTickEstimate(16);
        estimate.update(0, 20);
        estimate.update(1000, 0);
        estimate.update(2000, -5);
        assertFalse(estimate.hasEstimate());
    }

    @Test void resetClearsBothSamplesAndTheAnchor() {
        var estimate = new ServerTickEstimate(16);
        estimate.update(0, 20);
        estimate.update(1000, 20);
        assertTrue(estimate.hasEstimate());
        estimate.reset();
        assertFalse(estimate.hasEstimate());
        estimate.update(50_000, 20);
        assertFalse(estimate.hasEstimate(), "the first update after a reset is only an anchor");
    }

    @Test void sampleCountIsBoundedByTheConfiguredWindow() {
        var estimate = new ServerTickEstimate(4);
        for (int i = 0; i <= 20; i++) estimate.update(i * 1000L, 20);
        assertEquals(4, estimate.samples());
    }
}
