package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.util.math.Vec3d;

public class Jesus extends Module {

	public Jesus() {
		super("Jesus", Category.MOVEMENT, "Lets you walk on water");
	}

	@Override
	public void onUpdate() {
		// Sneaking lets you dive under the surface on purpose.
		if (mc.player.isSneaking()) {
			return;
		}
		if (mc.player.isTouchingWater() || mc.player.isInLava()) {
			Vec3d velocity = mc.player.getVelocity();
			mc.player.setVelocity(velocity.x, 0.1, velocity.z);
		}
	}
}
