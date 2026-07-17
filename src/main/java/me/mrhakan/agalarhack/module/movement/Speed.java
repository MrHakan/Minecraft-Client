package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

public class Speed extends Module {

	private static final float DEFAULT_WALK_SPEED = 0.1f;

	public Speed() {
		super("Speed", Category.MOVEMENT, "Makes you walk faster");
	}

	@Override
	public void selfSettings() {
		settings.addSetting("speed", 0.2);
	}

	@Override
	public void onUpdate() {
		mc.player.capabilities.setPlayerWalkSpeed((float) getNumberSetting("speed", 0.2));
	}

	@Override
	public void onDisable() {
		super.onDisable();
		if (mc.player != null) {
			mc.player.capabilities.setPlayerWalkSpeed(DEFAULT_WALK_SPEED);
		}
	}
}
