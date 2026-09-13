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
    /**
     * Half-width of the support probe. Narrow enough that it clears the ground the player is
     * standing on at any look-ahead the slider allows, wide enough to be a box vanilla can
     * intersect - a zero-width box intersects nothing, {@link net.minecraft.world.phys.AABB}
     * overlap being strict.
     */
    static final double PROBE_HALF_WIDTH = 0.05;
    /** The reach downwards the block-state probe had, kept so the module's feel does not change. */
    static final double PROBE_DEPTH = 0.2;

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
     * True when vanilla has no collision shape to stand on just beyond the current position.
     *
     * <p>The query has to be a collision query rather than a block-state test: a fence or a wall is
     * 1.5 blocks tall, so standing on one puts the player's feet inside the <em>air</em> block above
     * it, and classifying the block at y-0.2 called a continuous fence walkway a ledge and jumped at
     * every post. Slabs hid that, their collision being a full-width box inside their own block.
     *
     * <p>What it must <strong>not</strong> be is the player's own footprint swept forward. See
     * {@link #supportProbe}.
     */
    private boolean wouldFall(double x, double y, double z) {
        return !mc.level.getBlockCollisions(mc.player, supportProbe(x, y, z)).iterator().hasNext();
    }

    /**
     * The box tested for support: a narrow column at the look-ahead point, 0.2 deep.
     *
     * <p>Deliberately not the player's bounding box translated by the look-ahead vector. That box is
     * 0.6 wide, so with the default 0.35 look-ahead it still covers 0.25 blocks of the ground the
     * player is standing on, and ground under the probe means "supported" however far the ledge has
     * been passed. Solving for the edge: a swept footprint reports a fall only once the player's
     * centre is within {@code lookahead - 0.3} of the edge, which at the default is 0.05 - the
     * player's leading edge is then already a quarter of a block out over the void, and for any
     * look-ahead below 0.3, which the slider allows, it never reports one in time at all. The
     * look-ahead setting would have been measuring something 0.3 blocks shorter than it says.
     *
     * <p>A column keeps the trigger distance the setting promises, to within its own half-width.
     */
    static AABB supportProbe(double aheadX, double y, double aheadZ) {
        return new AABB(aheadX - PROBE_HALF_WIDTH, y - PROBE_DEPTH, aheadZ - PROBE_HALF_WIDTH,
                aheadX + PROBE_HALF_WIDTH, y, aheadZ + PROBE_HALF_WIDTH);
    }

}
