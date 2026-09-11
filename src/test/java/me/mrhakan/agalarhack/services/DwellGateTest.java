package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DwellGateTest {

    @Test
    void readyOnlyAfterTheTargetHasBeenHeldLongEnough() {
        DwellGate gate = new DwellGate();
        assertFalse(gate.ready(7, 3));
        assertFalse(gate.ready(7, 3));
        assertTrue(gate.ready(7, 3));
        assertTrue(gate.ready(7, 3), "and stays ready while it is still held");
    }

    @Test
    void sweepingPastSomethingNeverReachesTheThreshold() {
        DwellGate gate = new DwellGate();
        // Crosshair crossing three mobs in three ticks, which is what turning around looks like.
        assertFalse(gate.ready(1, 3));
        assertFalse(gate.ready(2, 3));
        assertFalse(gate.ready(3, 3));
    }

    @Test
    void changingTargetRestartsTheCount() {
        DwellGate gate = new DwellGate();
        gate.ready(7, 3);
        gate.ready(7, 3);
        assertFalse(gate.ready(8, 3), "the new target has been held for one tick, not three");
        assertEquals(1, gate.ticksOnTarget());
        assertEquals(8, gate.current());
    }

    @Test
    void losingTheTargetResetsRatherThanPausing() {
        DwellGate gate = new DwellGate();
        gate.ready(7, 3);
        gate.ready(7, 3);
        assertFalse(gate.ready(DwellGate.NONE, 3));
        assertEquals(0, gate.ticksOnTarget());
        assertEquals(DwellGate.NONE, gate.current());
        assertFalse(gate.ready(7, 3), "coming back to the same target starts over");
    }

    @Test
    void aZeroOrNegativeRequirementIsReadyImmediately() {
        assertTrue(new DwellGate().ready(7, 0));
        assertTrue(new DwellGate().ready(7, -5));
    }

    @Test
    void resetClearsEverything() {
        DwellGate gate = new DwellGate();
        gate.ready(7, 1);
        gate.reset();
        assertEquals(0, gate.ticksOnTarget());
        assertEquals(DwellGate.NONE, gate.current());
    }

    @Test
    void theCountSaturatesRatherThanWrapping() {
        DwellGate gate = new DwellGate();
        for (int tick = 0; tick < 1000; tick++) gate.ready(7, 3);
        assertTrue(gate.ticksOnTarget() > 0, "a long hold must never read as negative or new");
        assertTrue(gate.ready(7, 3));
    }
}
