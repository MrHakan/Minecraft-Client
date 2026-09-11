package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.BiFunction;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Input;
import org.junit.jupiter.api.Test;

/**
 * Guards the positional-argument hazard these helpers exist to remove: seven booleans in a row where
 * one transposed pair silently turns a jump into a strafe.
 */
class PlayerInputOverridesTest {

    private static Input none() {
        return new Input(false, false, false, false, false, false, false);
    }

    private static Input all() {
        return new Input(true, true, true, true, true, true, true);
    }

    private void changesOnlyItsOwnField(BiFunction<Input, Boolean, Input> override, Predicate<Input> field) {
        Input result = override.apply(none(), true);
        assertTrue(field.test(result), "the requested field was not set");
        // Exactly one of the seven may differ from the all-false input.
        int changed = 0;
        for (boolean value : new boolean[] { result.forward(), result.backward(), result.left(),
                result.right(), result.jump(), result.shift(), result.sprint() }) {
            if (value) changed++;
        }
        assertEquals(1, changed, "exactly one field may change: " + result);
    }

    @Test void forwardChangesOnlyForward() {
        changesOnlyItsOwnField(PlayerInputOverrides::withForward, Input::forward);
    }

    @Test void backwardChangesOnlyBackward() {
        changesOnlyItsOwnField(PlayerInputOverrides::withBackward, Input::backward);
    }

    @Test void jumpChangesOnlyJump() {
        changesOnlyItsOwnField(PlayerInputOverrides::withJump, Input::jump);
    }

    @Test void sprintChangesOnlySprint() {
        changesOnlyItsOwnField(PlayerInputOverrides::withSprint, Input::sprint);
    }

    @Test
    void everyOtherFieldSurvivesUntouched() {
        assertEquals(all(), PlayerInputOverrides.withForward(all(), true));
        assertEquals(all(), PlayerInputOverrides.withJump(all(), false));
    }

    @Test
    void neverTakesAKeyAwayFromThePlayer() {
        assertTrue(PlayerInputOverrides.withForward(all(), false).forward(),
                "releasing a key the player is holding would make the keyboard feel broken");
        assertTrue(PlayerInputOverrides.withJump(all(), false).jump());
        assertFalse(PlayerInputOverrides.withForward(none(), false).forward());
    }

    @Test
    void twoModulesSharingTheRecordDoNotClobberEachOther() {
        Input walking = PlayerInputOverrides.withForward(none(), true);
        Input jumping = PlayerInputOverrides.withJump(walking, true);
        assertTrue(jumping.forward());
        assertTrue(jumping.jump());
        // The opposite order has to reach the same place, which is the whole reason tick order is safe.
        assertEquals(jumping, PlayerInputOverrides.withForward(PlayerInputOverrides.withJump(none(), true), true));
    }
}
