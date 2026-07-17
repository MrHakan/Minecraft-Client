package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

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
		if (mc.player.isInWater() || mc.player.isInLava()) {
			mc.player.motionY = 0.1;
		}
	}
}
