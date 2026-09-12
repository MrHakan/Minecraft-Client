package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudScaleTest {
    @Test void aStoredScaleIsHeldToItsRange() {
        assertEquals(HudScale.MINIMUM, HudScale.clamp(0.1));
        assertEquals(HudScale.MAXIMUM, HudScale.clamp(9));
        assertEquals(1.25, HudScale.clamp(1.25));
    }

    /** A hand-edited file can hold anything; nothing here may come back as a NaN width. */
    @Test void nonsenseFallsBackToUnscaled() {
        assertEquals(HudScale.DEFAULT, HudScale.clamp(Double.NaN));
        assertEquals(HudScale.DEFAULT, HudScale.clamp(Double.POSITIVE_INFINITY));
    }

    @Test void logicalSizeShrinksAsTheScaleGrows() {
        assertEquals(400, HudScale.logical(400, 1.0));
        assertEquals(200, HudScale.logical(400, 2.0));
        assertEquals(800, HudScale.logical(400, 0.5));
    }

    /**
     * Floored, never rounded up. A widget anchored to the right edge is placed at
     * {@code logical - width}, and a logical edge past the real one puts it off screen.
     */
    @Test void aFractionalEdgeRoundsInwards() {
        assertEquals(266, HudScale.logical(400, 1.5));
        assertTrue(266 * 1.5 <= 400);
    }

    /** A zero-size screen must not collapse every clamp to an empty range. */
    @Test void sizeIsNeverZero() {
        assertEquals(1, HudScale.logical(0, 1.0));
        assertEquals(1, HudScale.logical(-40, 1.0));
    }

    @Test void aMousePositionMapsIntoTheSameSpaceTheWidgetsUse() {
        assertEquals(100.0, HudScale.toLogical(200.0, 2.0));
        assertEquals(200.0, HudScale.toLogical(200.0, 1.0));
    }
}
