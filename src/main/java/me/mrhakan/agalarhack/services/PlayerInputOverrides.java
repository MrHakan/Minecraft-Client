package me.mrhakan.agalarhack.services;

import net.minecraft.world.entity.player.Input;

/**
 * Where modules ask for movement keys to be held, and where the input mixin collects those requests.
 *
 * <p>{@code Input} is an all-or-nothing record of seven booleans, so a module holding one key has to
 * rebuild the whole thing - and a single transposed pair makes the player strafe instead of jump
 * with nothing to say so. Requests here are named, merged in one place, and turned into the record
 * exactly once.
 *
 * <p>Requests only ever turn a key <em>on</em>. Nothing here takes a key away from the player:
 * releasing a key they are physically holding would make the keyboard feel broken, so a module that
 * wants to stop simply stops requesting. That is also what makes two modules safe to run together -
 * requests merge rather than overwrite, so tick order between them does not matter.
 */
public final class PlayerInputOverrides {
    private PlayerInputOverrides() { }

    /**
     * What the modules asked for this tick, or null when nobody asked.
     *
     * <p>Modules run at the end of a client tick; the player's keys are rebuilt from the keyboard
     * near the start of the next one, inside {@code aiStep}, and read for movement a few lines
     * later. A module that wrote {@code player.input.keyPresses} directly was therefore writing to a
     * field that vanilla overwrote before ever reading it - which is exactly what AutoWalk and
     * Parkour did, silently doing nothing at all in a running game while ticking perfectly happily.
     *
     * <p>So modules record a request here instead and the input mixin applies it at the one moment
     * it survives: after vanilla has rebuilt the keys and before it uses them.
     */
    private static Request pending;

    /** Only ever turns keys on; see the class note on why nothing here releases a key. */
    private record Request(boolean forward, boolean backward, boolean jump, boolean sprint) {
        Input applyTo(Input keys) {
            return new Input(keys.forward() || forward, keys.backward() || backward,
                    keys.left(), keys.right(), keys.jump() || jump, keys.shift(),
                    keys.sprint() || sprint);
        }
    }

    /**
     * Asks for keys to be held for the next input tick. Requests merge, so two modules asking for
     * different keys both get them and tick order between them does not matter.
     */
    public static void request(boolean forward, boolean backward, boolean jump, boolean sprint) {
        Request previous = pending;
        pending = previous == null
                ? new Request(forward, backward, jump, sprint)
                : new Request(previous.forward() || forward, previous.backward() || backward,
                        previous.jump() || jump, previous.sprint() || sprint);
    }

    /**
     * Applies and clears the pending request. Called once per input tick by the mixin.
     *
     * <p>Clearing is what makes a disabled module stop immediately: it simply stops requesting, and
     * the next input tick has nothing to apply. There is no state to unwind and no key left held.
     */
    public static Input consume(Input keys) {
        Request request = pending;
        pending = null;
        return request == null || keys == null ? keys : request.applyTo(keys);
    }

    /** A world change or disconnect must not leave a request from the old one waiting. */
    public static void clear() {
        pending = null;
    }
}
