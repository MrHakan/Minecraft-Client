package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.util.math.Vec3d;

public class Speed extends Module {

	public Speed() {
		super("Speed", Category.MOVEMENT, "Makes you move faster on the ground");
	}

	@Override
	public void selfSettings() {
		settings.addSetting("multiplier", 1.2);
	}

	@Override
	public void onUpdate() {
		if (!mc.player.isOnGround()) {
			return;
		}
		if (mc.player.input.movementForward == 0 && mc.player.input.movementSideways == 0) {
			return;
		}
		// Ground friction counteracts the per-tick multiplier, so the speed
		// settles at an equilibrium instead of growing forever.
		double multiplier = getNumberSetting("multiplier", 1.2);
		Vec3d velocity = mc.player.getVelocity();
		mc.player.setVelocity(velocity.x * multiplier, velocity.y, velocity.z * multiplier);
	}
}
