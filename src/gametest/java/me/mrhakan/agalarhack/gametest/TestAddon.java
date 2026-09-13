package me.mrhakan.agalarhack.gametest;

import me.mrhakan.agalarhack.api.AddonContext;
import me.mrhakan.agalarhack.api.AgalarHackAddon;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

/**
 * A real addon, loaded by the real Fabric loader.
 *
 * <p>Declared as an {@code agalarhack} entrypoint in the game test mod's own
 * {@code fabric.mod.json}, which is exactly how somebody else's addon declares itself. That is the
 * point: the loader, the entrypoint key, the metadata and the registration all get exercised
 * together. A unit test with a hand-made context would only prove the hand-made context works.
 *
 * <p>Test source only — it is never in the shipped jar.
 */
public class TestAddon implements AgalarHackAddon {
    public static final String MODULE = "GametestAddonModule";
    public static final String COMMAND = "gametestaddon";

    /** What the addon was told about itself, for the scenario to check against the loader's record. */
    public static volatile String seenId;
    public static volatile String seenName;
    public static volatile boolean ran;

    @Override
    public void onAgalarHackReady(AddonContext context) {
        seenId = context.id();
        seenName = context.name();
        ran = true;
        context.logger().info("test addon loading against API version {}",
                me.mrhakan.agalarhack.api.AgalarHackApi.version());
        context.addModule(new Module(MODULE, Category.MISC, "Registered by the game test addon") { });
        context.addCommand(new Command(COMMAND, "Registered by the game test addon", COMMAND) {
            @Override public void onCommand(String[] args) { }
        });
    }
}
