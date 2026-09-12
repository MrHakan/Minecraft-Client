package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LookControllerTest {
    private static LookController.Aim aim(float yaw, float pitch) {
        return new LookController.Aim(yaw, pitch, true, 180, 45, 30);
    }

    @Test void nothingIsAskedForUntilSomethingIsAimed() {
        var look = new LookController();
        assertFalse(look.active());
        assertNull(look.tick(0, 0));
    }

    @Test void anAimIsRenewedEveryTickUntilTheViewArrives() {
        var look = new LookController();
        look.aimAt(aim(90, 0), 40);
        assertNotNull(look.tick(0, 0));
        assertNotNull(look.tick(45, 0));
        assertTrue(look.active());
        assertNull(look.tick(90, 0), "arriving finishes the aim");
        assertFalse(look.active());
    }

    /**
     * A goal the step limit cannot reach, or a view held still by an open screen, must not hold the
     * player's rotation for the rest of the session.
     */
    @Test void theBudgetRunsOutAndLetsGo() {
        var look = new LookController();
        look.aimAt(aim(90, 0), 3);
        assertNotNull(look.tick(0, 0));
        assertNotNull(look.tick(0, 0));
        assertNull(look.tick(0, 0), "the third tick spends the last of the budget");
        assertFalse(look.active());
    }

    @Test void arrivalIsMeasuredTheShortWayRound() {
        assertTrue(LookController.arrived(359.9f, 0, 0.1f, 0));
        assertTrue(LookController.arrived(-179.9f, 0, 180f, 0));
        assertFalse(LookController.arrived(170, 0, -170, 0));
    }

    @Test void pitchIsNotWrapped() {
        assertTrue(LookController.arrived(0, 89.7f, 0, 90f));
        assertFalse(LookController.arrived(0, -90, 0, 90));
    }

    @Test void cancellingStopsAskingImmediately() {
        var look = new LookController();
        look.aimAt(aim(90, 0), 40);
        look.cancel();
        assertFalse(look.active());
        assertNull(look.tick(0, 0));
    }

    @Test void aNonsenseAimIsRefusedRatherThanStored() {
        assertThrows(IllegalArgumentException.class, () -> new LookController.Aim(Float.NaN, 0, true, 180, 45, 30));
        assertThrows(IllegalArgumentException.class, () -> new LookController.Aim(0, 0, true, 0, 45, 30));
        var look = new LookController();
        look.aimAt(null, 40);
        assertFalse(look.active());
        look.aimAt(aim(90, 0), 0);
        assertFalse(look.active(), "a budget of nothing is not an aim");
    }

    @Test void aimingAgainReplacesTheGoalAndRefillsTheBudget() {
        var look = new LookController();
        look.aimAt(aim(90, 0), 2);
        look.tick(0, 0);
        look.aimAt(aim(-90, 0), 20);
        assertEquals(20, look.remainingTicks());
        assertEquals(-90, look.tick(0, 0).yaw());
    }
}
