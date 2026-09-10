package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

/** Enables the target card rendered from the shared TargetTracker. */
public class TargetHUD extends Module {
    public TargetHUD() {
        super("TargetHUD", Category.RENDER, "Shows the last active combat target with health and distance");
    }

    @Override
    public void selfSettings() {
        addBooleanSetting("showHealth", true, "Show target current/max health");
        addBooleanSetting("showDistance", true, "Show distance to the target");
        addBooleanSetting("showArmor", true, "Show the player's armor value when the target is a player");
        addNumberSetting("timeout", 3.0, 0.5, 10.0, "How long a stale target remains visible in seconds");
    }
}
