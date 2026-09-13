package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BreadcrumbTrailTest {
    @Test void theFirstSampleIsAlwaysRecorded() {
        var trail = new BreadcrumbTrail(64, 2.0);
        assertTrue(trail.sample(0, 0, 0, 0));
        assertEquals(1, trail.size());
    }

    @Test void standingStillAddsNothing() {
        var trail = new BreadcrumbTrail(64, 2.0);
        trail.sample(0, 0, 0, 0);
        for (int tick = 1; tick < 100; tick++) assertFalse(trail.sample(0, 0, 0, tick));
        assertEquals(1, trail.size());
    }

    @Test void samplingFollowsDistanceNotTime() {
        var trail = new BreadcrumbTrail(64, 2.0);
        trail.sample(0, 0, 0, 0);
        assertFalse(trail.sample(1, 0, 0, 1), "inside the minimum distance");
        assertTrue(trail.sample(2, 0, 0, 2), "exactly the minimum distance");
        assertTrue(trail.sample(4, 0, 0, 3));
        assertEquals(3, trail.size());
    }

    @Test void verticalMovementCountsToo() {
        var trail = new BreadcrumbTrail(64, 2.0);
        trail.sample(0, 0, 0, 0);
        assertTrue(trail.sample(0, 3, 0, 1));
    }

    @Test void theOldestPointIsDroppedWhenFull() {
        var trail = new BreadcrumbTrail(3, 1.0);
        for (int i = 0; i < 5; i++) trail.sample(i * 2, 0, 0, i);
        assertEquals(3, trail.size());
        assertEquals(4.0, trail.snapshot().get(0).x(), 1e-9);
    }

    @Test void expiryDropsOnlyOldPointsAndOnlyWhenEnabled() {
        var trail = new BreadcrumbTrail(64, 1.0);
        trail.sample(0, 0, 0, 0);
        trail.sample(5, 0, 0, 100);
        trail.sample(10, 0, 0, 200);
        trail.expire(250, 0);
        assertEquals(3, trail.size(), "a non-positive age keeps everything");
        // At t=250 an age limit of 200 drops only the point from t=0.
        trail.expire(250, 200);
        assertEquals(2, trail.size());
        assertEquals(5.0, trail.snapshot().get(0).x(), 1e-9);
        // Tightening it to 120 also drops t=100, leaving only the newest point.
        trail.expire(250, 120);
        assertEquals(1, trail.size());
        assertEquals(10.0, trail.snapshot().get(0).x(), 1e-9);
    }

    @Test void nonFiniteCoordinatesAreIgnored() {
        var trail = new BreadcrumbTrail(64, 1.0);
        assertFalse(trail.sample(Double.NaN, 0, 0, 0));
        assertFalse(trail.sample(0, Double.POSITIVE_INFINITY, 0, 0));
        assertEquals(0, trail.size());
    }

    @Test void configurationIsBoundedInBothDirections() {
        var trail = new BreadcrumbTrail(0, -5);
        trail.sample(0, 0, 0, 0);
        trail.sample(100, 0, 0, 1);
        trail.sample(200, 0, 0, 2);
        assertEquals(2, trail.size(), "capacity floors at two points");

        var huge = new BreadcrumbTrail(Integer.MAX_VALUE, Double.NaN);
        huge.sample(0, 0, 0, 0);
        assertTrue(huge.sample(2, 0, 0, 1), "a non-finite distance falls back to a usable default");
    }

    @Test void shrinkingCapacityEvictsImmediately() {
        var trail = new BreadcrumbTrail(10, 1.0);
        for (int i = 0; i < 10; i++) trail.sample(i * 2, 0, 0, i);
        trail.configure(3, 1.0);
        assertEquals(3, trail.size());
    }

    @Test void clearDropsEverything() {
        var trail = new BreadcrumbTrail(10, 1.0);
        trail.sample(0, 0, 0, 0);
        trail.clear();
        assertEquals(0, trail.size());
        assertTrue(trail.snapshot().isEmpty());
    }
}
