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
		restoreCaptured();
	}

	/**
	 * Records the abilities of whichever player is current, handing the previous one back first.
	 *
	 * <p>The guard used to be {@code capturedPlayer != null}, which made this a no-op for every
	 * player after the first - and so made {@code onUpdate}'s {@code capturedPlayer != mc.player}
	 * branch, the one written to notice a replacement, permanently dead. A respawn builds a new
	 * LocalPlayer without a world change to announce it, so the record went on pointing at the
	 * discarded one: flight was switched on for the live player and handed back to a player nobody
	 * could see any more, leaving the real one with mayfly, flying and the module's speed still set.
	 * Step does the same thing correctly and was the model for this.
	 */
	private void captureState() {
		if (mc.player == null || capturedPlayer == mc.player) {
			return;
		}
		restoreCaptured();
		previousMayfly = mc.player.getAbilities().mayfly;
		previousFlying = mc.player.getAbilities().flying;
		previousFlyingSpeed = mc.player.getAbilities().getFlyingSpeed();
		capturedPlayer = mc.player;
	}

	private void restoreCaptured() {
		if (capturedPlayer != null) {
			capturedPlayer.getAbilities().mayfly = previousMayfly;
			capturedPlayer.getAbilities().flying = previousFlying;
			capturedPlayer.getAbilities().setFlyingSpeed(previousFlyingSpeed);
		}
		capturedPlayer = null;
	}
}
