package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

public class Sprint extends Module {

	public Sprint() {
		super("Sprint", Category.MOVEMENT, "Automatically sprints while moving forward");
	}

	@Override
	public void onUpdate() {
		if (mc.player.movementInput.moveForward > 0
				&& !mc.player.collidedHorizontally
				&& !mc.player.isSneaking()
				&& !mc.player.isHandActive()) {
			mc.player.setSprinting(true);
		}
	}

	@Override
	public void onDisable() {
		super.onDisable();
		if (mc.player != null) {
			mc.player.setSprinting(false);
		}
	}
}
