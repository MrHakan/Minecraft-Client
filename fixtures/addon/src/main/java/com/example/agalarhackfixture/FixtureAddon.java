package com.example.agalarhackfixture;

import me.mrhakan.agalarhack.api.AddonContext;
import me.mrhakan.agalarhack.api.AgalarHackAddon;
import me.mrhakan.agalarhack.api.AgalarHackApi;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

/**
 * A third-party addon, in its own package, compiled and packaged into its own jar.
 *
 * <p>This exists because the in-tree game-test addon proves the entrypoint and nothing about
 * packaging: its classes are on the client's own classpath and its entrypoint is declared in the
 * game-test mod's {@code fabric.mod.json}. This one is built as a separate artifact with its own
 * mod metadata and is discovered by the Fabric loader from that jar, which is what an installed
 * addon actually is.
 *
 * <p>Deliberately not under {@code me.mrhakan.agalarhack}: if any of this compiled only because it
 * shared a package with the client, that would be worth finding out here rather than from an addon
 * author.
 */
public class FixtureAddon implements AgalarHackAddon {
    public static final String MODULE = "ExternalFixtureModule";
    public static final String COMMAND = "externalfixture";

    @Override
    public void onAgalarHackReady(AddonContext context) {
        // The version check exactly as docs/ADDONS.md recommends it, through the method rather than
        // a constant. If this were a constant, javac would inline it and this check would compare
        // the literal against itself in a jar that was built once and then run against anything.
        if (AgalarHackApi.version() != 1) {
            context.logger().warn("built against API 1, found {}; not registering",
                    AgalarHackApi.version());
            return;
        }
        context.logger().info("external addon {} {} registering", context.id(), context.version());
        context.addModule(new Module(MODULE, Category.MISC, "Registered by an external addon jar") {
            @Override
            public void selfSettings() {
                // A setting of the addon's own, so the test can tell whether the client applied
                // settings to a module that arrived from outside its own jar.
                addBooleanSetting("externalFlag", true, "Declared by the external addon");
            }
        });
        context.addCommand(new Command(COMMAND, "Registered by an external addon jar", COMMAND) {
            @Override public void onCommand(String[] args) { }
        });
    }
}
