package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class Speed extends Module {

	public Speed() {
		super("Speed", Category.MOVEMENT, "Boosts ground movement with a configurable horizontal speed cap");
	}

	@Override
	public void selfSettings() {
		addNumberSetting("multiplier", 1.2, 1.0, 3.0, "Horizontal velocity multiplier applied while moving on ground");
		addNumberSetting("maxSpeed", 0.65, 0.1, 1.5, "Maximum horizontal velocity in blocks per tick");
		addBooleanSetting("inFluids", false, "Allow the boost while standing in water or lava");
		addBooleanSetting("whileSneaking", false, "Allow the boost while sneaking");
	}

	@Override
	public void onUpdate() {
		if (mc.player == null || !mc.player.onGround()) {
			return;
		}
		if (!getBooleanSetting("inFluids", false) && (mc.player.isInWater() || mc.player.isInLava())) {
			return;
		}
		if (!getBooleanSetting("whileSneaking", false) && mc.player.isShiftKeyDown()) {
			return;
		}

		Vec2 move = mc.player.input.getMoveVector();
		if (move.x == 0.0F && move.y == 0.0F) {
			return;
		}

		double multiplier = getNumberSetting("multiplier", 1.2);
		double maxSpeed = getNumberSetting("maxSpeed", 0.65);
		Vec3 velocity = mc.player.getDeltaMovement();
		double x = velocity.x * multiplier;
		double z = velocity.z * multiplier;
		double horizontal = Math.hypot(x, z);
		// The cap bounds the boost; it must never brake. It applies to the whole horizontal speed,
		// and its range goes down to 0.1 blocks per tick - below a vanilla sprint at roughly 0.28 -
		// so a low setting used to leave the player slower with the module on than with it off,
		// which is a strange thing for something called Speed to do. Never returning less than the
		// player already had makes the low end of the range harmless instead of a trap.
		double unboosted = Math.hypot(velocity.x, velocity.z);
		double target = Math.max(Math.min(horizontal, maxSpeed), unboosted);
		if (horizontal > 0.0 && target < horizontal) {
			double scale = target / horizontal;
			x *= scale;
			z *= scale;
		}
		mc.player.setDeltaMovement(x, velocity.y, z);
	}
}
