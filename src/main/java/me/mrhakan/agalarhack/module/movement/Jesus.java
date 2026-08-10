package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.phys.Vec3;

public class Jesus extends Module {

	public Jesus() {
		super("Jesus", Category.MOVEMENT, "Lets you walk on water");
	}

	@Override
	public void onUpdate() {
		// Sneaking lets you dive under the surface on purpose.
		if (mc.player.isShiftKeyDown()) {
			return;
		}
		if (mc.player.isInWater() || mc.player.isInLava()) {
			Vec3 velocity = mc.player.getDeltaMovement();
			mc.player.setDeltaMovement(velocity.x, 0.1, velocity.z);
		}
	}
}
