package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.AABB;

public class NoFall extends Module {

	private boolean sentForCurrentFall;

	public NoFall() {
		super("NoFall", Category.MOVEMENT,
				"Sends one grounded-status packet near a predicted landing; servers may still apply fall damage");
	}

	@Override
	public void selfSettings() {
		addNumberSetting("threshold", 3.0, 2.0, 20.0,
				"Minimum fall distance before checking for a landing on the next movement step");
	}

	@Override
	public void onEnable() {
		sentForCurrentFall = false;
	}

	@Override
	public void onDisable() {
		sentForCurrentFall = false;
	}

	@Override
	public void onUpdate() {
		if (mc.player == null || mc.player.connection == null) {
			return;
		}

		double threshold = getNumberSetting("threshold", 3.0);
		if (mc.player.onGround() || mc.player.fallDistance < threshold) {
			sentForCurrentFall = false;
			return;
		}

		if (!sentForCurrentFall && landingOnNextMovementStep()) {
			mc.player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
			sentForCurrentFall = true;
		}
	}

	/**
	 * Delays the single status report until the player's next downward move intersects real block
	 * collision. Reporting grounded in open air can reset the local/server fall state too early and
	 * still result in damage when the player actually lands.
	 */
    private boolean landingOnNextMovementStep() {
        var movement = mc.player.getDeltaMovement();
        if (movement.y >= 0 || mc.level == null) return false;
        AABB currentBounds = mc.player.getBoundingBox();
        AABB sweptBounds = currentBounds.expandTowards(movement.x, movement.y, movement.z);
        double nextBottom = currentBounds.minY + movement.y;
        for (var shape : mc.level.getBlockCollisions(mc.player, sweptBounds)) {
            AABB collision = shape.bounds();
            // Only count a support surface crossed by the player's feet. A side wall intersecting
            // the swept body is not a landing and must not reset the fall state.
            if (currentBounds.intersects(collision)) continue;
            if (collision.maxY <= currentBounds.minY + 1.0e-4
                    && collision.maxY >= nextBottom - 1.0e-4) return true;
        }
        return false;
    }
}
