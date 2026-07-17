package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

public class Step extends Module {

	private static final float DEFAULT_STEP_HEIGHT = 0.6f;

	public Step() {
		super("Step", Category.MOVEMENT, "Lets you walk up full blocks without jumping");
	}

	@Override
	public void selfSettings() {
		settings.addSetting("height", 1.0);
	}

	@Override
	public void onUpdate() {
		mc.player.stepHeight = (float) getNumberSetting("height", 1.0);
	}

	@Override
	public void onDisable() {
		super.onDisable();
		if (mc.player != null) {
			mc.player.stepHeight = DEFAULT_STEP_HEIGHT;
		}
	}
}
