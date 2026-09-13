package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

public class Sprint extends Module {
    private net.minecraft.client.player.LocalPlayer capturedPlayer;
    private boolean previousSprint;
    private boolean appliedSprint;

	public Sprint() {
		super("Sprint", Category.MOVEMENT, "Automatically sprints forward while respecting vanilla sprint eligibility");
	}

	@Override
	public void selfSettings() {
		addBooleanSetting("whileUsing", false, "Keep auto-sprint active while using an item");
		addBooleanSetting("whileSneaking", false, "Keep auto-sprint active while sneaking");
	}

	@Override
	public void onUpdate() {
		if (mc.player == null) {
			return;
		}

        if (capturedPlayer != mc.player) {
            onDisable(); capturedPlayer = mc.player; previousSprint = mc.player.isSprinting();
        }
		boolean forward = mc.player.input.hasForwardImpulse();
		boolean allowedUsing = getBooleanSetting("whileUsing", false) || !mc.player.isUsingItem();
		boolean allowedSneaking = getBooleanSetting("whileSneaking", false) || !mc.player.isShiftKeyDown();
		boolean shouldSprint = forward
				&& !mc.player.horizontalCollision
				&& allowedUsing
				&& allowedSneaking
				&& mc.player.canSprint();

		mc.player.setSprinting(shouldSprint);
        appliedSprint = shouldSprint;
	}

	@Override
	public void onDisable() {
        if (capturedPlayer != null && capturedPlayer.isSprinting() == appliedSprint) capturedPlayer.setSprinting(previousSprint);
        capturedPlayer = null;
	}
}
