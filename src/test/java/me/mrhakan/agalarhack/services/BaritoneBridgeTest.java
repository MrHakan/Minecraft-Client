package me.mrhakan.agalarhack.services;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The bridge reaches Baritone entirely by name, so these run it against a stub that carries the
 * documented names and shapes. What is being checked is that the reflection targets the real API —
 * a typo in a method name is invisible to the compiler and would only ever show up as a silent
 * "Baritone did nothing" for a player who has it installed.
 *
 * <p>The absent case — which is nearly every player — is deliberately not tested here. The stub is on
 * this classpath by design, so absence cannot be arranged without inventing a class-loader seam that
 * exists only for the test. It is covered where it is real instead: the game test runs the shipped
 * client, which has no Baritone at all, and asserts both the message and that nothing reached chat.
 */
class BaritoneBridgeTest {
    private BaritoneBridge bridge;

    @BeforeEach void setUp() {
        BaritoneAPI.reset();
        bridge = new BaritoneBridge();
        bridge.forget();
    }

    @Test void theApiIsFoundWhenItIsOnTheClasspath() {
        assertTrue(bridge.available());
    }

    @Test void pathingToABlockReachesSetGoalAndPath() {
        assertEquals(BaritoneBridge.Result.STARTED, bridge.pathTo(10, 64, -20));
        assertInstanceOf(GoalBlock.class, BaritoneAPI.lastGoal);
        GoalBlock goal = (GoalBlock) BaritoneAPI.lastGoal;
        assertEquals(10, goal.x);
        assertEquals(64, goal.y);
        assertEquals(-20, goal.z);
    }

    @Test void pathingToAColumnUsesTheTwoArgumentGoal() {
        assertEquals(BaritoneBridge.Result.STARTED, bridge.pathTo(100, -200));
        assertInstanceOf(GoalXZ.class, BaritoneAPI.lastGoal);
        GoalXZ goal = (GoalXZ) BaritoneAPI.lastGoal;
        assertEquals(100, goal.x);
        assertEquals(-200, goal.z);
    }

    @Test void cancellingReachesCancelEverything() {
        assertEquals(BaritoneBridge.Result.STARTED, bridge.cancel());
        assertEquals(1, BaritoneAPI.cancels);
    }

    @Test void thePathingStateIsRead() {
        assertFalse(bridge.pathing());
        BaritoneAPI.pathing = true;
        assertTrue(bridge.pathing());
    }

}
