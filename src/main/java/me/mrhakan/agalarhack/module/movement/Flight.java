package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

public class Flight extends Module {

	private net.minecraft.client.player.LocalPlayer capturedPlayer;
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
		if (capturedPlayer != mc.player) {
			captureState();
		}
		mc.player.getAbilities().mayfly = true;
		mc.player.getAbilities().flying = true;
		mc.player.getAbilities().setFlyingSpeed((float) getNumberSetting("speed", 0.1));
	}

	@Override
	public void onDisable() {
		if (capturedPlayer != null) {
			capturedPlayer.getAbilities().mayfly = previousMayfly;
			capturedPlayer.getAbilities().flying = previousFlying;
			capturedPlayer.getAbilities().setFlyingSpeed(previousFlyingSpeed);
		}
		capturedPlayer = null;
	}

	private void captureState() {
		if (mc.player == null || capturedPlayer != null) {
			return;
		}
		previousMayfly = mc.player.getAbilities().mayfly;
		previousFlying = mc.player.getAbilities().flying;
		previousFlyingSpeed = mc.player.getAbilities().getFlyingSpeed();
		capturedPlayer = mc.player;
	}
}
