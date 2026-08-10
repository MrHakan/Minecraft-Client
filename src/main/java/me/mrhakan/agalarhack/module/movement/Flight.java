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
		mc.player.getAbilities().mayfly = true;
		mc.player.getAbilities().flying = true;
		mc.player.getAbilities().setFlyingSpeed((float) getNumberSetting("speed", 0.1));
	}

	@Override
	public void onDisable() {
		if (mc.player != null) {
			if (!mc.player.getAbilities().instabuild) {
				mc.player.getAbilities().mayfly = false;
				mc.player.getAbilities().flying = false;
			}
			mc.player.getAbilities().setFlyingSpeed(DEFAULT_FLY_SPEED);
		}
	}
}
