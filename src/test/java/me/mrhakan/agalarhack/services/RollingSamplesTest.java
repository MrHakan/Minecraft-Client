package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RollingSamplesTest {
    @Test void statisticsOfAnEmptyRingAreZeroRatherThanUndefined() {
        var samples = new RollingSamples(8);
        assertTrue(samples.isEmpty());
        assertEquals(0, samples.average());
        assertEquals(0, samples.minimum());
        assertEquals(0, samples.maximum());
        assertEquals(0, samples.latest());
        assertEquals(0, samples.snapshot().length);
    }

    @Test void snapshotIsOldestFirstAndWrapsCorrectly() {
        var samples = new RollingSamples(3);
        samples.add(1);
        samples.add(2);
        samples.add(3);
        assertArrayEquals(new double[] { 1, 2, 3 }, samples.snapshot());
        samples.add(4);
        assertArrayEquals(new double[] { 2, 3, 4 }, samples.snapshot());
        assertEquals(3, samples.size());
    }

    @Test void statisticsUseOnlyRetainedSamples() {
        var samples = new RollingSamples(3);
        for (double value : new double[] { 100, 1, 2, 3 }) samples.add(value);
        assertEquals(2.0, samples.average(), 1e-9);
        assertEquals(1.0, samples.minimum(), 1e-9);
        assertEquals(3.0, samples.maximum(), 1e-9);
        assertEquals(3.0, samples.latest(), 1e-9);
    }

    @Test void nonFiniteSamplesAreIgnoredRatherThanPoisoningEverything() {
        var samples = new RollingSamples(4);
        samples.add(10);
        samples.add(Double.NaN);
        samples.add(Double.POSITIVE_INFINITY);
        assertEquals(1, samples.size());
        assertEquals(10.0, samples.average(), 1e-9);
    }

    @Test void capacityIsBounded() {
        assertEquals(1, new RollingSamples(0).capacity());
        assertEquals(1, new RollingSamples(-4).capacity());
        assertEquals(RollingSamples.MAX_CAPACITY, new RollingSamples(Integer.MAX_VALUE).capacity());
    }

    @Test void clearResetsTheRing() {
        var samples = new RollingSamples(4);
        samples.add(5);
        samples.clear();
        assertTrue(samples.isEmpty());
        samples.add(7);
        assertArrayEquals(new double[] { 7 }, samples.snapshot());
    }
}
