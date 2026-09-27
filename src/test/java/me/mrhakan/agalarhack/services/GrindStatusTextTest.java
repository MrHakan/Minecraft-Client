package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GrindStatusTextTest {
    @Test void runningStatusUsesHumanStepNumbersAndTask() {
        assertEquals("AutoGrind 3/7: craft stone_pickaxe (0/1)", GrindStatusText.format(
                TaskRunner.State.RUNNING, "craft stone_pickaxe (0/1)", 2, 7, null, null));
    }

    @Test void pauseAndFailureKeepTheirActionableReason() {
        assertEquals("AutoGrind paused: move closer to 4 64 -2", GrindStatusText.format(
                TaskRunner.State.NEEDS_MOVEMENT, null, 0, 2, "move closer to 4 64 -2", null));
        assertEquals("AutoGrind failed: furnace occupied", GrindStatusText.format(
                TaskRunner.State.FAILED, null, 0, 2, null, "furnace occupied"));
    }

    @Test void incompleteInputsHaveSafeReadableFallbacks() {
        assertEquals("AutoGrind idle", GrindStatusText.format(null, null, -1, 0, null, null));
        assertEquals("AutoGrind 1/1: preparing next step", GrindStatusText.format(
                TaskRunner.State.RUNNING, " ", -4, 0, null, null));
        assertEquals("AutoGrind paused: manual movement required", GrindStatusText.format(
                TaskRunner.State.NEEDS_MOVEMENT, null, 0, 0, "", null));
    }
}
