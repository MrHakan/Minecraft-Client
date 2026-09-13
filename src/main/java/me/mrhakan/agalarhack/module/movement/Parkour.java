package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.PlayerInputOverrides;
import net.minecraft.world.phys.AABB;

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
        // Its own predicate, not merely whether it is switched on: SafeWalk has modes, and in
        // "sneaking" it holds nothing while the player stands up straight. Asking isToggled() meant
        // that in that mode neither module did anything - SafeWalk correctly stood aside, and
        // Parkour refused to jump because it believed SafeWalk had the edge.
        if (SafeWalk.shouldHoldEdge()) return;

        var motion = player.getDeltaMovement();
        double speed = Math.hypot(motion.x, motion.z);
        if (speed < 0.08) return;

        double lookahead = getNumberSetting("lookahead", 0.35);
        double aheadX = player.getX() + motion.x / speed * lookahead;
        double aheadZ = player.getZ() + motion.z / speed * lookahead;
        if (!wouldFall(aheadX, player.getY(), aheadZ)) return;

        PlayerInputOverrides.request(false, false, true, false);
    }

    /**
     * Probe the player's projected footprint just below their feet, using vanilla block collision
     * shapes. A fence/wall reaches into the air block above its own position, so classifying the
     * block at y-0.2 cannot tell whether the player actually has support. This is a small local
     * collision query on a moving, grounded player, never a scanner or an entity-collision query.
     */
    private boolean wouldFall(double x, double y, double z) {
        var player = mc.player;
        var box = player.getBoundingBox();
        double dx = x - player.getX(), dz = z - player.getZ();
        // Exclude zero-area contact at the outside edge, while keeping the existing 0.2 drop depth.
        double inset = 1.0E-4;
        AABB feet = new AABB(box.minX + dx + inset, y - 0.2, box.minZ + dz + inset,
                box.maxX + dx - inset, y, box.maxZ + dz - inset);
        return !mc.level.getBlockCollisions(player, feet).iterator().hasNext();
    }

}
