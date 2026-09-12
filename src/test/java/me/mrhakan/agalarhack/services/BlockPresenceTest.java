package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Only the part of {@link BlockPresence} that can be tested without a client.
 *
 * <p>A {@code BlockState} cannot be built without the block registry, so the predicate itself is
 * covered where it matters instead: SpawnESP, HoleESP and Parkour each have a game test scenario
 * that depends on this answer being right about real blocks in a real world. What is checked here is
 * the contract those cannot reach - that the two methods stay each other's opposite, and that a null
 * state is answered rather than thrown at.
 */
class BlockPresenceTest {
    @Test void nullIsPassableRatherThanAnException() {
        assertFalse(BlockPresence.blocksMotion(null));
        assertTrue(BlockPresence.isPassable(null));
    }

    @Test void theTwoQuestionsAreOpposites() {
        assertNotEquals(BlockPresence.blocksMotion(null), BlockPresence.isPassable(null));
    }
}
