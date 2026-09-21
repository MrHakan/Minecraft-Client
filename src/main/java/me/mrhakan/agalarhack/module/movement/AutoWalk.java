package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.NotificationService;
import me.mrhakan.agalarhack.services.PlayerInputOverrides;

/** Keeps walking without a held key while retaining explicit safety/pause conditions. */
public class AutoWalk extends Module {
    private int blockedTicks;

    public AutoWalk() {
        super("AutoWalk", Category.MOVEMENT, "Keeps walking without holding the key, with screen, health, hunger and obstacle safety stops");
    }

    @Override public void selfSettings() {
        addChoiceSetting("direction", "forward", "Which way to walk", "forward", "backward");
        addBooleanSetting("sprint", false, "Also hold sprint");
        addBooleanSetting("pauseOnScreen", true, "Pause while a screen such as chat or an inventory is open");
        addBooleanSetting("pauseWhenHungry", false, "Pause before hunger becomes low enough to make sprinting unreliable");
        addNumberSetting("hungerThreshold", 7, 1, 19, "Pause at or below this hunger level when hunger pause is enabled");
        addBooleanSetting("pauseOnLowHealth", false, "Pause walking when health falls below a safety threshold");
        addNumberSetting("healthThreshold", 8, 1, 19, "Pause at or below this many health points when low-health pause is enabled");
        addBooleanSetting("stopWhenBlocked", true, "Turn off after walking into something for a while");
        addNumberSetting("blockedSeconds", 3, 1, 30, "How long to keep pushing into an obstacle before turning off");
    }

    @Override public void onEnable() { blockedTicks = 0; }
    @Override public void onDisable() { blockedTicks = 0; }
    @Override public void onDisconnect() { blockedTicks = 0; }

    @Override public void onUpdate() {
        var player = mc.player;
        if (player == null || mc.level == null) { blockedTicks = 0; return; }
        if (getBooleanSetting("pauseOnScreen", true) && mc.gui.screen() != null) { blockedTicks = 0; return; }
        if (getBooleanSetting("pauseWhenHungry", false)
                && player.getFoodData().getFoodLevel() <= (int) Math.round(getNumberSetting("hungerThreshold", 7))) {
            blockedTicks = 0;
            return;
        }
        if (getBooleanSetting("pauseOnLowHealth", false)
                && player.getHealth() <= (float) getNumberSetting("healthThreshold", 8)) {
            blockedTicks = 0;
            return;
        }
        if (getBooleanSetting("stopWhenBlocked", true) && player.horizontalCollision && !player.onClimbable()) {
            int limit = (int) Math.round(getNumberSetting("blockedSeconds", 3) * 20.0);
            if (++blockedTicks >= limit) {
                blockedTicks = 0;
                service(NotificationService.class).publish(NotificationService.Type.INFO,
                        "AutoWalk stopped: walked into something");
                toggle();
                return;
            }
        } else blockedTicks = 0;

        boolean backward = "backward".equals(getStringSetting("direction", "forward"));
        boolean sprint = !backward && getBooleanSetting("sprint", false);
        PlayerInputOverrides.request(!backward, backward, false, sprint);
    }
}
