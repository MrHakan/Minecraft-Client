package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TargetRotationTest {

    @Test
    void emptyCandidatesMeanNothingToAttack() {
        TargetRotation rotation = new TargetRotation(10);
        assertEquals(TargetRotation.NONE, rotation.select(0, new int[0], 1));
        assertEquals(TargetRotation.NONE, rotation.select(0, null, 1));
        assertEquals(TargetRotation.NONE, rotation.current());
    }

    @Test
    void takesTheBestCandidateWithNothingHeld() {
        TargetRotation rotation = new TargetRotation(10);
        assertEquals(7, rotation.select(0, new int[] { 7, 8, 9 }, 1));
    }

    @Test
    void holdsTheCurrentTargetWhileABetterOneIsStillFresh() {
        TargetRotation rotation = new TargetRotation(10);
        rotation.select(0, new int[] { 7, 8 }, 1);
        // 8 is now ranked first, but 7 is still valid and the delay has not passed.
        assertEquals(7, rotation.select(5, new int[] { 8, 7 }, 1));
        assertEquals(7, rotation.select(9, new int[] { 8, 7 }, 1));
    }

    @Test
    void switchesOnceTheBetterTargetHasStayedBetter() {
        TargetRotation rotation = new TargetRotation(10);
        rotation.select(0, new int[] { 7, 8 }, 1);
        assertEquals(8, rotation.select(10, new int[] { 8, 7 }, 1));
    }

    @Test
    void losingTheTargetSwitchesImmediatelyRatherThanWaiting() {
        TargetRotation rotation = new TargetRotation(10);
        rotation.select(0, new int[] { 7, 8 }, 1);
        // 7 died or left range; there is nothing left to hold on to.
        assertEquals(8, rotation.select(1, new int[] { 8 }, 1),
                "the switch delay must not apply when the current target is gone");
    }

    @Test
    void aZeroDelayNeverHolds() {
        TargetRotation rotation = new TargetRotation(0);
        rotation.select(0, new int[] { 7, 8 }, 1);
        assertEquals(8, rotation.select(0, new int[] { 8, 7 }, 1));
    }

    @Test
    void aNegativeDelayIsTreatedAsZero() {
        TargetRotation rotation = new TargetRotation(-5);
        rotation.select(0, new int[] { 7, 8 }, 1);
        assertEquals(8, rotation.select(0, new int[] { 8, 7 }, 1));
    }

    @Test
    void twoEquallyGoodTargetsCannotMakeItFlipEveryTick() {
        TargetRotation rotation = new TargetRotation(10);
        rotation.select(0, new int[] { 7, 8 }, 1);
        int switches = 0;
        int previous = 7;
        // The priority order alternates every tick, which is exactly what "closest" does when two
        // targets circle each other. Without the delay this would switch a hundred times.
        for (int tick = 1; tick <= 100; tick++) {
            int chosen = rotation.select(tick, tick % 2 == 0 ? new int[] { 7, 8 } : new int[] { 8, 7 }, 1);
            if (chosen != previous) { switches++; previous = chosen; }
        }
        assertTrue(switches <= 100 / 10, "one switch per delay window at most, but got " + switches);
        assertTrue(switches > 0, "and it must still be able to switch at all");
    }

    @Test
    void multiTargetSpreadsAcrossTheTopCandidates() {
        TargetRotation rotation = new TargetRotation(10);
        int[] candidates = { 1, 2, 3, 4 };
        assertEquals(1, rotation.select(0, candidates, 3));
        rotation.attacked();
        assertEquals(2, rotation.select(1, candidates, 3));
        rotation.attacked();
        assertEquals(3, rotation.select(2, candidates, 3));
        rotation.attacked();
        assertEquals(1, rotation.select(3, candidates, 3), "wraps back rather than reaching the fourth");
    }

    @Test
    void multiTargetRotationAdvancesOnAttacksNotOnTicks() {
        TargetRotation rotation = new TargetRotation(10);
        int[] candidates = { 1, 2 };
        for (int tick = 0; tick < 20; tick++) {
            assertEquals(1, rotation.select(tick, candidates, 2),
                    "nothing landed, so the rotation must not run ahead of the attack timing");
        }
        rotation.attacked();
        assertEquals(2, rotation.select(21, candidates, 2));
    }

    @Test
    void aShrinkingCandidateListCannotIndexPastItsEnd() {
        TargetRotation rotation = new TargetRotation(10);
        for (int index = 0; index < 5; index++) rotation.attacked();
        // Four attacks in, but only one candidate left: must still be a valid choice.
        assertEquals(9, rotation.select(6, new int[] { 9 }, 4));
    }

    @Test
    void maxTargetsIsClampedToWhatIsActuallyThere() {
        TargetRotation rotation = new TargetRotation(10);
        assertEquals(5, rotation.select(0, new int[] { 5 }, 8));
        rotation.attacked();
        assertEquals(5, rotation.select(1, new int[] { 5 }, 8));
    }

    @Test
    void maxTargetsBelowOneStillAttacksSomething() {
        TargetRotation rotation = new TargetRotation(10);
        assertEquals(5, rotation.select(0, new int[] { 5, 6 }, 0));
        assertEquals(5, rotation.select(1, new int[] { 5, 6 }, -3));
    }

    @Test
    void resetForgetsTheHeldTargetAndTheRotation() {
        TargetRotation rotation = new TargetRotation(10);
        rotation.select(0, new int[] { 7, 8 }, 1);
        rotation.attacked();
        rotation.reset();
        assertEquals(TargetRotation.NONE, rotation.current());
        assertEquals(8, rotation.select(1, new int[] { 8, 7 }, 1), "no held target means no hold");
        rotation.reset();
        assertEquals(1, rotation.select(2, new int[] { 1, 2 }, 2), "the round robin restarts at the top");
    }

    @Test
    void runningOutOfTargetsClearsTheHold() {
        TargetRotation rotation = new TargetRotation(10);
        rotation.select(0, new int[] { 7, 8 }, 1);
        assertEquals(TargetRotation.NONE, rotation.select(1, new int[0], 1));
        assertNotEquals(7, rotation.select(2, new int[] { 8, 7 }, 1),
                "after the fight ended, the old target has no claim on the next one");
    }

    /**
     * Landing hits must not postpone a switch forever.
     *
     * <p>The existing delay test never lands an attack, which is exactly where this hid: `attacked`
     * also stamped the switch-delay window, so every landed hit restarted it. With Aura's default
     * eight-tick delay and any weapon swinging faster than that - bare hands are five - the window
     * never elapsed and the aura held its first target for the whole fight, whatever the priority
     * ordering said.
     */
    @Test
    void landedHitsDoNotPostponeASwitchForever() {
        TargetRotation rotation = new TargetRotation(8);
        assertEquals(7, rotation.select(0, new int[] { 7, 8 }, 1));

        int chosen = 7;
        for (int tick = 1; tick <= 60 && chosen != 8; tick++) {
            chosen = rotation.select(tick, new int[] { 8, 7 }, 1);
            if (tick % 5 == 0) rotation.attacked();
        }
        assertEquals(8, chosen, "a better target that stayed better for sixty ticks was never taken");
    }

    /**
     * Dropping from several targets to one must not freeze the choice.
     *
     * <p>The multi-target branch set the current target without ever recording when, so the single
     * target branch measured its delay from Long.MIN_VALUE. That subtraction overflows to a large
     * negative, which is always below the delay, so the window never expired and the stale pick was
     * held until something reset it.
     */
    @Test
    void droppingToASingleTargetStillRespectsTheDelayRatherThanFreezing() {
        TargetRotation rotation = new TargetRotation(10);
        assertEquals(1, rotation.select(0, new int[] { 1, 2 }, 2));
        assertEquals(2, rotation.select(20, new int[] { 2, 1 }, 1),
                "twenty ticks is well past the ten-tick delay");
    }
}
