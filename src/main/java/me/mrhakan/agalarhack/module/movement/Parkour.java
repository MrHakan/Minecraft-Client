package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.PlayerInputOverrides;
import net.minecraft.core.BlockPos;

/**
 * Jumps at the edge of a block instead of walking off it.
 *
 * <p>Deliberately conservative, as the roadmap asks: it only acts while genuinely walking on solid
 * ground, and it refuses to act at all while SafeWalk is holding the same edge, because the two
 * would otherwise fight over every ledge.
 */
public class Parkour extends Module {
    public Parkour() {
        super("Parkour", Category.MOVEMENT, "Jumps at block edges instead of walking off them");
        markExperimental();
    }

    @Override
    public void selfSettings() {
        addNumberSetting("lookahead", 0.35, 0.1, 1.0, "How far ahead to check for a drop, in blocks");
        addBooleanSetting("requireSprint", false, "Only jump while sprinting");
        addBooleanSetting("ignoreSneaking", true, "Do not jump while sneaking");
    }

    @Override
    public void onUpdate() {
        var player = mc.player;
        if (player == null || mc.level == null || mc.gui.screen() != null) return;
        if (!player.onGround() || player.isPassenger() || player.isInWater() || player.isInLava()) return;
        if (getBooleanSetting("ignoreSneaking", true) && player.isShiftKeyDown()) return;
        if (getBooleanSetting("requireSprint", false) && !player.isSprinting()) return;
        // SafeWalk stops you at the edge; jumping from it would defeat the module the player enabled.
        if (safeWalkHolding()) return;

        var motion = player.getDeltaMovement();
        double speed = Math.hypot(motion.x, motion.z);
        if (speed < 0.08) return;

        double lookahead = getNumberSetting("lookahead", 0.35);
        double aheadX = player.getX() + motion.x / speed * lookahead;
        double aheadZ = player.getZ() + motion.z / speed * lookahead;
        if (!wouldFall(aheadX, player.getY(), aheadZ)) return;

        PlayerInputOverrides.request(false, false, true, false);
    }

    /** True when there is nothing to stand on just beyond the current position. */
    private boolean wouldFall(double x, double y, double z) {
        BlockPos below = BlockPos.containing(x, y - 0.2, z);
        return !mc.level.getBlockState(below).blocksMotion();
    }

    private static boolean safeWalkHolding() {
        var module = AgalarHackClient.moduleManager == null ? null
                : AgalarHackClient.moduleManager.getModule("SafeWalk");
        return module != null && module.isToggled();
    }
}
