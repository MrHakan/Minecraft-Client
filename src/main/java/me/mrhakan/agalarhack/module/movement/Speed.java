package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

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
		if (!mc.player.onGround()) {
			return;
		}
		Vec2 move = mc.player.input.getMoveVector();
		if (move.x == 0.0F && move.y == 0.0F) {
			return;
		}
		// Ground friction counteracts the per-tick multiplier, so the speed
		// settles at an equilibrium instead of growing forever.
		double multiplier = getNumberSetting("multiplier", 1.2);
		Vec3 velocity = mc.player.getDeltaMovement();
		mc.player.setDeltaMovement(velocity.x * multiplier, velocity.y, velocity.z * multiplier);
	}
}
