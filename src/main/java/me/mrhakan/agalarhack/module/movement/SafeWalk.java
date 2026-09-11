package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.client.Minecraft;

/**
 * Stops the player walking off block edges.
 *
 * <p>Rather than reimplementing edge detection, this reuses the check vanilla already performs while
 * sneaking, so the behaviour is exactly vanilla's and needs no movement prediction of its own. The
 * mixin reads {@link #shouldHoldEdge()} and nothing else, which keeps the injected code trivial.
 */
public class SafeWalk extends Module {
    public SafeWalk() {
        super("SafeWalk", Category.MOVEMENT, "Prevents walking off block edges using Minecraft's own sneak-edge check");
        // Re-marked. Its scenario failed about one run in ten because the pit was dug wherever the
        // previous scenario left the player, sometimes on top of the scene's own blocks. That is
        // fixed, but a badge asserts that a check keeps the module honest, and this one has not yet
        // earned that back.
        markExperimental();
    }

    @Override
    public void selfSettings() {
        addChoiceSetting("mode", "always", "When edge protection applies", "always", "sneaking", "not_sneaking");
        addBooleanSetting("onlyOnGround", true, "Only hold edges while standing on a block");
    }

    /** Called from the mixin on the client's own player only. */
    public static boolean shouldHoldEdge() {
        var manager = AgalarHackClient.moduleManager;
        if (manager == null) return false;
        var module = manager.getModule("SafeWalk");
        if (!(module instanceof SafeWalk safeWalk) || !safeWalk.isToggled()) return false;
        return safeWalk.active();
    }

    private boolean active() {
        Minecraft client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || client.level == null) return false;
        if (getBooleanSetting("onlyOnGround", true) && !player.onGround()) return false;
        return switch (getStringSetting("mode", "always")) {
            case "sneaking" -> player.isShiftKeyDown();
            case "not_sneaking" -> !player.isShiftKeyDown();
            default -> true;
        };
    }
}
