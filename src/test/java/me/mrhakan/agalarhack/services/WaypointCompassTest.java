package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaypointCompassTest {
    @Test void aTargetStraightAheadReadsAsZero() {
        // Yaw 0 faces +Z, so a target further along +Z is dead ahead.
        assertEquals(0.0, WaypointCompass.relativeBearing(0, 0, 0, 10, 0), 1e-6);
        assertEquals('↑', WaypointCompass.arrow(0));
    }

    @Test void aTargetBehindReadsAsHalfATurn() {
        assertEquals(180.0, Math.abs(WaypointCompass.relativeBearing(0, 0, 0, -10, 0)), 1e-6);
        assertEquals('↓', WaypointCompass.arrow(180));
        assertEquals('↓', WaypointCompass.arrow(-180));
    }

    @Test void positiveIsRightAndNegativeIsLeft() {
        // Facing +Z, the player's right hand points toward -X.
        assertTrue(WaypointCompass.relativeBearing(0, 0, -10, 0, 0) > 0, "-X should be to the right");
        assertTrue(WaypointCompass.relativeBearing(0, 0, 10, 0, 0) < 0, "+X should be to the left");
        assertEquals('→', WaypointCompass.arrow(90));
        assertEquals('←', WaypointCompass.arrow(-90));
    }

    @Test void turningThePlayerRotatesTheBearing() {
        double ahead = WaypointCompass.relativeBearing(0, 0, 0, 10, 0);
        double turned = WaypointCompass.relativeBearing(0, 0, 0, 10, 90);
        assertEquals(0.0, ahead, 1e-6);
        assertEquals(-90.0, turned, 1e-6);
    }

    @Test void bearingsAlwaysStayInsideTheWrapRange() {
        for (float yaw = -720; yaw <= 720; yaw += 17) {
            for (int angle = 0; angle < 360; angle += 13) {
                double x = Math.cos(Math.toRadians(angle)) * 50;
                double z = Math.sin(Math.toRadians(angle)) * 50;
                double bearing = WaypointCompass.relativeBearing(0, 0, x, z, yaw);
                assertTrue(bearing >= -180.0 && bearing <= 180.0, "bearing " + bearing);
            }
        }
    }

    @Test void wrapHandlesMultipleTurnsAndNonFiniteInput() {
        assertEquals(10.0, WaypointCompass.wrapDegrees(370), 1e-9);
        assertEquals(-10.0, WaypointCompass.wrapDegrees(-370), 1e-9);
        assertEquals(-180.0, WaypointCompass.wrapDegrees(180));
        assertEquals(0.0, WaypointCompass.wrapDegrees(Double.NaN));
        assertEquals(0.0, WaypointCompass.wrapDegrees(Double.POSITIVE_INFINITY));
    }

    @Test void standingOnTheWaypointIsAheadRatherThanUndefined() {
        assertEquals(0.0, WaypointCompass.relativeBearing(5, 5, 5, 5, 42));
    }

    @Test void everyArrowSectorIsReachableAndOrdered() {
        char[] expected = { '↑', '↗', '→', '↘', '↓', '↙', '←', '↖' };
        for (int i = 0; i < 8; i++) {
            assertEquals(expected[i], WaypointCompass.arrow(i * 45), "sector " + i);
        }
    }
}
