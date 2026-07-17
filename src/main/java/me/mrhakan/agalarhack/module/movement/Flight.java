package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

public class Flight extends Module {

	private static final float DEFAULT_FLY_SPEED = 0.05f;

	public Flight() {
		super("Flight", Category.MOVEMENT, "Lets you fly like in creative mode");
	}

	@Override
	public void selfSettings() {
		settings.addSetting("speed", 0.1);
	}

	@Override
	public void onUpdate() {
		mc.player.capabilities.isFlying = true;
		mc.player.capabilities.setFlySpeed((float) getNumberSetting("speed", 0.1));
	}

	@Override
	public void onDisable() {
		super.onDisable();
		if (mc.player != null) {
			if (!mc.player.capabilities.isCreativeMode) {
				mc.player.capabilities.isFlying = false;
			}
			mc.player.capabilities.setFlySpeed(DEFAULT_FLY_SPEED);
		}
	}
}
