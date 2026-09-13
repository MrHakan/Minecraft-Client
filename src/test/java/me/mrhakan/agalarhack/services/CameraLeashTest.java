package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CameraLeashTest {
    private static final CameraLeash.Point ORIGIN = new CameraLeash.Point(0, 0, 0);

    @Test void aCameraInsideTheRadiusIsLeftAlone() {
        var inside = new CameraLeash.Point(3, 4, 0);
        assertSame(inside, CameraLeash.clamp(ORIGIN, inside, 10));
    }

    @Test void aCameraOutsideIsPulledBackOntoTheRadius() {
        var outside = new CameraLeash.Point(30, 40, 0);
        var clamped = CameraLeash.clamp(ORIGIN, outside, 10);
        assertEquals(10, CameraLeash.distance(ORIGIN, clamped), 1e-9);
        // Same direction, shorter: 3-4-5 scaled to ten is 6-8.
        assertEquals(6, clamped.x(), 1e-9);
        assertEquals(8, clamped.y(), 1e-9);
    }

    @Test void theLimitIsMeasuredInThreeDimensions() {
        var up = new CameraLeash.Point(0, 100, 0);
        assertEquals(24, CameraLeash.distance(ORIGIN, CameraLeash.clamp(ORIGIN, up, 24)), 1e-9);
    }

    /** Zero is the opt-out, and it must not be confused with "clamp to the anchor". */
    @Test void noLimitLeavesTheCameraWhereItIs() {
        var far = new CameraLeash.Point(500, 0, 0);
        assertSame(far, CameraLeash.clamp(ORIGIN, far, 0));
        assertSame(far, CameraLeash.clamp(ORIGIN, far, -5));
    }

    @Test void anAnchorAwayFromTheOriginStillHoldsTheCamera() {
        var anchor = new CameraLeash.Point(100, 64, -200);
        var camera = new CameraLeash.Point(160, 64, -200);
        var clamped = CameraLeash.clamp(anchor, camera, 24);
        assertEquals(124, clamped.x(), 1e-9);
        assertEquals(64, clamped.y(), 1e-9);
        assertEquals(-200, clamped.z(), 1e-9);
    }

    /** A camera exactly on the anchor has no direction to be pulled along; it must not divide by zero. */
    @Test void aCameraOnTheAnchorSurvives() {
        var clamped = CameraLeash.clamp(ORIGIN, ORIGIN, 24);
        assertEquals(0, CameraLeash.distance(ORIGIN, clamped), 1e-9);
    }

    @Test void nothingIsAssumedAboutNullOrNonsense() {
        var camera = new CameraLeash.Point(1, 2, 3);
        assertSame(camera, CameraLeash.clamp(null, camera, 24));
        assertNull(CameraLeash.clamp(ORIGIN, null, 24));
        assertSame(camera, CameraLeash.clamp(ORIGIN, camera, Double.NaN));
        assertEquals(0, CameraLeash.distance(null, camera));
    }
}
