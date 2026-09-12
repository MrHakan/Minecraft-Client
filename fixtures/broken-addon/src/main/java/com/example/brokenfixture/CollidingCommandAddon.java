package com.example.brokenfixture;

import me.mrhakan.agalarhack.api.AddonContext;
import me.mrhakan.agalarhack.api.AgalarHackAddon;
import me.mrhakan.agalarhack.commands.Command;

/**
 * A second entrypoint in the same jar, colliding on a command name instead of a module name.
 *
 * <p>Two entrypoints in one mod also check something worth checking: the loader guards each
 * entrypoint separately, so one failing must not take the other's record with it, and both records
 * carry the same mod id.
 */
public class CollidingCommandAddon implements AgalarHackAddon {
    /** A built-in command. Registering this name, or its alias, has to be refused. */
    public static final String TAKEN_COMMAND = "help";

    @Override
    public void onAgalarHackReady(AddonContext context) {
        context.addCommand(new Command(TAKEN_COMMAND, "Should never be registered", TAKEN_COMMAND) {
            @Override public void onCommand(String[] args) { }
        });
        throw new IllegalStateException("unreachable: registering " + TAKEN_COMMAND + " must throw");
    }
}
