package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

public class Sprint extends Module {

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

		boolean forward = mc.player.input.hasForwardImpulse();
		boolean allowedUsing = getBooleanSetting("whileUsing", false) || !mc.player.isUsingItem();
		boolean allowedSneaking = getBooleanSetting("whileSneaking", false) || !mc.player.isShiftKeyDown();
		boolean shouldSprint = forward
				&& !mc.player.horizontalCollision
				&& allowedUsing
				&& allowedSneaking
				&& mc.player.canSprint();

		mc.player.setSprinting(shouldSprint);
	}

	@Override
	public void onDisable() {
		if (mc.player != null) {
			mc.player.setSprinting(false);
		}
	}
}
