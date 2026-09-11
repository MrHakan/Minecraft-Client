package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.NotificationService;
import me.mrhakan.agalarhack.services.PlayerInputOverrides;

/**
 * Holds the movement key so a long walk does not need a held key or a wedged one.
 *
 * <p>Only ever adds a key press; it never takes one away, so the player can always steer, stop by
 * pressing the opposite direction, or do anything else while it runs. The stop conditions are the
 * point of the module rather than extras: walking into a wall for ten minutes, or into a screen, is
 * exactly the failure a "hold the key down with something heavy" workaround produces.
 */
public class AutoWalk extends Module {
    /** Ticks of continuous horizontal collision before giving up; roughly a second at 20 tps. */
    private int blockedTicks;

    public AutoWalk() {
        super("AutoWalk", Category.MOVEMENT, "Keeps walking without holding the key, and stops when walking stops working");
    }

    @Override
    public void selfSettings() {
        addChoiceSetting("direction", "forward", "Which way to walk", "forward", "backward");
        addBooleanSetting("sprint", false, "Also hold sprint");
        addBooleanSetting("pauseOnScreen", true, "Pause while a screen such as chat or an inventory is open");
        addBooleanSetting("stopWhenBlocked", true, "Turn off after walking into something for a while");
        addNumberSetting("blockedSeconds", 3, 1, 30, "How long to keep pushing into an obstacle before turning off");
    }

    @Override public void onEnable() { blockedTicks = 0; }
    @Override public void onDisable() { blockedTicks = 0; }
    @Override public void onDisconnect() { blockedTicks = 0; }

    @Override
    public void onUpdate() {
        var player = mc.player;
        if (player == null || mc.level == null) { blockedTicks = 0; return; }

        // A screen means the player is typing or sorting, not walking. Vanilla already ignores the
        // real key here; overriding it anyway would be the one case where this module surprises.
        if (getBooleanSetting("pauseOnScreen", true) && mc.gui.screen() != null) { blockedTicks = 0; return; }

        if (getBooleanSetting("stopWhenBlocked", true) && player.horizontalCollision && !player.onClimbable()) {
            int limit = (int) Math.round(getNumberSetting("blockedSeconds", 3) * 20.0);
            if (++blockedTicks >= limit) {
                blockedTicks = 0;
                service(NotificationService.class).publish(NotificationService.Type.INFO,
                        "AutoWalk stopped: walked into something");
                toggle();
                return;
            }
        } else {
            blockedTicks = 0;
        }

        boolean backward = "backward".equals(getStringSetting("direction", "forward"));
        // Sprinting backwards is not a thing in vanilla, so asking for it would only mislead.
        boolean sprint = !backward && getBooleanSetting("sprint", false);
        PlayerInputOverrides.request(!backward, backward, false, sprint);
    }
}
