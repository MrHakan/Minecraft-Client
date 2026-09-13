package com.example.brokenfixture;

import me.mrhakan.agalarhack.api.AddonContext;
import me.mrhakan.agalarhack.api.AgalarHackAddon;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

/**
 * An addon that collides with a built-in module name, in its own jar.
 *
 * <p>The client's promise is that a broken addon costs its author a bug report and nothing else:
 * the client still starts, every other addon still loads, and what this one managed to register
 * before it failed stays registered. All three are claims about a real installation, so this is a
 * real installed jar rather than a hand-made context.
 *
 * <p>The order below is the point. The command registers first and must survive; the module name is
 * already taken by a built-in and must be refused, which aborts the rest of this addon's setup.
 */
public class BrokenAddon implements AgalarHackAddon {
    /** A name no built-in uses, so its survival is evidence rather than a coincidence. */
    public static final String COMMAND = "brokenfixture";

    /** A built-in module. Registering this name has to be refused. */
    public static final String TAKEN_MODULE = "Fullbright";

    @Override
    public void onAgalarHackReady(AddonContext context) {
        context.addCommand(new Command(COMMAND, "Registered before this addon failed", COMMAND) {
            @Override public void onCommand(String[] args) { }
        });
        context.logger().info("about to collide with the built-in {} on purpose", TAKEN_MODULE);
        context.addModule(new Module(TAKEN_MODULE, Category.MISC, "Should never be registered") { });
        throw new IllegalStateException("unreachable: registering " + TAKEN_MODULE + " must throw");
    }
}
