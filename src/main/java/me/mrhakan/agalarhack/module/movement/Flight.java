package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

public class Flight extends Module {

	private boolean captured;
	private boolean previousMayfly;
	private boolean previousFlying;
	private float previousFlyingSpeed;

	public Flight() {
		super("Flight", Category.MOVEMENT, "Provides client-side creative-style flight while preserving prior abilities");
	}

	@Override
	public void selfSettings() {
		addNumberSetting("speed", 0.1, 0.02, 1.0, "Creative-style flying speed multiplier");
	}

	@Override
	public void onEnable() {
		captureState();
	}

	@Override
	public void onUpdate() {
		if (mc.player == null) {
			return;
		}
		if (!captured) {
			captureState();
		}
		mc.player.getAbilities().mayfly = true;
		mc.player.getAbilities().flying = true;
		mc.player.getAbilities().setFlyingSpeed((float) getNumberSetting("speed", 0.1));
	}

	@Override
	public void onDisable() {
		if (mc.player != null && captured) {
			mc.player.getAbilities().mayfly = previousMayfly;
			mc.player.getAbilities().flying = previousFlying;
			mc.player.getAbilities().setFlyingSpeed(previousFlyingSpeed);
		}
		captured = false;
	}

	private void captureState() {
		if (mc.player == null || captured) {
			return;
		}
		previousMayfly = mc.player.getAbilities().mayfly;
		previousFlying = mc.player.getAbilities().flying;
		previousFlyingSpeed = mc.player.getAbilities().getFlyingSpeed();
		captured = true;
	}
}
