package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

public class Sprint extends Module {

	public Sprint() {
		super("Sprint", Category.MOVEMENT, "Automatically sprints while moving forward");
	}

	@Override
	public void onUpdate() {
		if (mc.player.input.movementForward > 0
				&& !mc.player.horizontalCollision
				&& !mc.player.isSneaking()
				&& !mc.player.isUsingItem()) {
			mc.player.setSprinting(true);
		}
	}

	@Override
	public void onDisable() {
		if (mc.player != null) {
			mc.player.setSprinting(false);
		}
	}
}
