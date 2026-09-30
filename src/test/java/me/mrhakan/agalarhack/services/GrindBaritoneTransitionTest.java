package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrindBaritoneTransitionTest {
    @Test void ownedCancellationWaitsForTheSafeSegmentToStopButCannotWaitForever() {
        var transition = new GrindBaritoneTransition();
        assertFalse(transition.waiting(true, false, false));
        transition.cancelled();
        for (int i = 0; i < 100; i++) assertTrue(transition.waiting(true, false, false));
        assertFalse(transition.waiting(true, false, false));
        transition.cancelled();
        assertFalse(transition.waiting(false, false, false));
        assertFalse(transition.waiting(true, false, false));
    }
    @Test void anotherActiveProcessDoesNotInheritTheOwnedCancellationGrace() {
        var transition = new GrindBaritoneTransition();
        transition.cancelled();
        assertFalse(transition.waiting(true, true, false));
        assertFalse(transition.waiting(true, false, false));
        transition.cancelled();
        assertFalse(transition.waiting(true, false, true));
    }
}
