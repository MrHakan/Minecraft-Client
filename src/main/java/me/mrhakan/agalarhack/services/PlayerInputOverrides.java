package me.mrhakan.agalarhack.services;

import net.minecraft.world.entity.player.Input;

/**
 * Single-field edits to the player's input record.
 *
 * <p>{@code Input} is an all-or-nothing record, so a module that wants to hold one key has to
 * rebuild the whole thing and copy the other six back. Written by hand that is seven positional
 * booleans per call site, where a single transposed pair makes the player strafe instead of jump and
 * nothing tells you. It is also what lets two modules share the record safely: each reads the
 * current value, changes only its own field and preserves the rest, so tick order between them stops
 * mattering.
 *
 * <p>These deliberately only ever turn a key <em>on</em> when asked to. Nothing here takes a key away
 * from the player: releasing a key the player is physically holding would make the keyboard feel
 * broken, so a module that wants to stop simply stops overriding.
 */
public final class PlayerInputOverrides {
    private PlayerInputOverrides() { }

    public static Input withForward(Input keys, boolean forward) {
        return new Input(keys.forward() || forward, keys.backward(), keys.left(), keys.right(),
                keys.jump(), keys.shift(), keys.sprint());
    }

    public static Input withBackward(Input keys, boolean backward) {
        return new Input(keys.forward(), keys.backward() || backward, keys.left(), keys.right(),
                keys.jump(), keys.shift(), keys.sprint());
    }

    public static Input withJump(Input keys, boolean jump) {
        return new Input(keys.forward(), keys.backward(), keys.left(), keys.right(),
                keys.jump() || jump, keys.shift(), keys.sprint());
    }

    public static Input withSprint(Input keys, boolean sprint) {
        return new Input(keys.forward(), keys.backward(), keys.left(), keys.right(),
                keys.jump(), keys.shift(), keys.sprint() || sprint);
    }
}
