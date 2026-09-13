package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.entity.player.Input;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Guards the positional-argument hazard this class exists to remove - seven booleans in a row where
 * one transposed pair silently turns a jump into a strafe - and the consume-once contract the input
 * mixin depends on.
 */
class PlayerInputOverridesTest {

    private static Input none() {
        return new Input(false, false, false, false, false, false, false);
    }

    private static Input all() {
        return new Input(true, true, true, true, true, true, true);
    }

    /** Requests are static, so a leftover from one test would change the next one's result. */
    @BeforeEach void discardAnyPendingRequest() {
        PlayerInputOverrides.clear();
    }

    @Test void forwardReachesForwardAndNothingElse() {
        PlayerInputOverrides.request(true, false, false, false);
        Input result = PlayerInputOverrides.consume(none());
        assertTrue(result.forward());
        assertEquals(1, setFields(result), "exactly one field may change: " + result);
    }

    @Test void jumpReachesJumpAndNothingElse() {
        PlayerInputOverrides.request(false, false, true, false);
        Input result = PlayerInputOverrides.consume(none());
        assertTrue(result.jump());
        assertEquals(1, setFields(result), "exactly one field may change: " + result);
    }

    @Test void backwardAndSprintLandInTheirOwnFields() {
        PlayerInputOverrides.request(false, true, false, true);
        Input result = PlayerInputOverrides.consume(none());
        assertTrue(result.backward());
        assertTrue(result.sprint());
        assertFalse(result.forward());
        assertEquals(2, setFields(result));
    }

    @Test void keysTheModuleDidNotAskForAreLeftAlone() {
        PlayerInputOverrides.request(true, false, false, false);
        assertEquals(all(), PlayerInputOverrides.consume(all()));
    }

    @Test void neverTakesAKeyAwayFromThePlayer() {
        PlayerInputOverrides.request(false, false, false, false);
        Input result = PlayerInputOverrides.consume(all());
        assertTrue(result.forward(), "releasing a key the player is holding would feel broken");
        assertTrue(result.jump());
    }

    @Test void twoModulesRequestingDifferentKeysBothGetThem() {
        PlayerInputOverrides.request(true, false, false, false);
        PlayerInputOverrides.request(false, false, true, false);
        Input result = PlayerInputOverrides.consume(none());
        assertTrue(result.forward());
        assertTrue(result.jump());
    }

    @Test void theOppositeRequestOrderReachesTheSamePlace() {
        PlayerInputOverrides.request(false, false, true, false);
        PlayerInputOverrides.request(true, false, false, false);
        Input first = PlayerInputOverrides.consume(none());
        PlayerInputOverrides.request(true, false, false, false);
        PlayerInputOverrides.request(false, false, true, false);
        assertEquals(first, PlayerInputOverrides.consume(none()));
    }

    /** The mixin runs every input tick; a request that survived one would hold the key forever. */
    @Test void aRequestIsAppliedExactlyOnce() {
        PlayerInputOverrides.request(true, false, false, false);
        assertTrue(PlayerInputOverrides.consume(none()).forward());
        assertFalse(PlayerInputOverrides.consume(none()).forward(),
                "a module that stopped asking must stop holding the key on the very next tick");
    }

    @Test void consumingWithNoRequestReturnsTheKeysUntouched() {
        assertEquals(none(), PlayerInputOverrides.consume(none()));
        assertEquals(all(), PlayerInputOverrides.consume(all()));
    }

    @Test void clearDropsARequestThatWasNeverApplied() {
        PlayerInputOverrides.request(true, false, false, false);
        PlayerInputOverrides.clear();
        assertFalse(PlayerInputOverrides.consume(none()).forward());
    }

    private static int setFields(Input input) {
        int count = 0;
        for (boolean value : new boolean[] { input.forward(), input.backward(), input.left(),
                input.right(), input.jump(), input.shift(), input.sprint() }) {
            if (value) count++;
        }
        return count;
    }
}
