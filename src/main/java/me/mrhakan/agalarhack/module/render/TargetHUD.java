package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

/** Enables the target card rendered from the shared TargetTracker. */
public class TargetHUD extends Module {
    public TargetHUD() {
        super("TargetHUD", Category.RENDER, "Shows the active combat target with health, gear and status effects");
    }

    @Override
    public void selfSettings() {
        addBooleanSetting("showHealth", true, "Show target current/max health text");
        addBooleanSetting("healthBar", true, "Draw a proportional target health bar");
        addBooleanSetting("showDistance", true, "Show distance to the target");
        addBooleanSetting("showArmor", true, "Show the player's numeric armor value when the target is a player");
        addBooleanSetting("showEquipment", true, "Show target armor plus main/offhand item icons");
        addBooleanSetting("showEffects", true, "Show status-effect icons when the client knows them");
        addNumberSetting("maxEffects", 6.0, 1.0, 12.0, "Maximum number of status-effect icons to show");
        addNumberSetting("timeout", 3.0, 0.5, 10.0, "How long a stale target remains visible in seconds");
    }
}
