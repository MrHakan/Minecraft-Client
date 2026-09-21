package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.phys.Vec2;

public class Sprint extends Module {
    private net.minecraft.client.player.LocalPlayer capturedPlayer;
    private boolean appliedSprint;

	public Sprint() {
		super("Sprint", Category.MOVEMENT, "Automatically sprints while respecting vanilla sprint eligibility and player input");
	}

	@Override
	public void selfSettings() {
		addBooleanSetting("whileUsing", false, "Keep auto-sprint active while using an item");
		addBooleanSetting("whileSneaking", false, "Keep auto-sprint active while sneaking");
		addBooleanSetting("omni", false, "Allow auto-sprint while moving sideways or backward when vanilla sprint eligibility permits it");
	}

	@Override
	public void onUpdate() {
		if (mc.player == null) {
			return;
		}

        if (capturedPlayer != mc.player) {
            onDisable();
            capturedPlayer = mc.player;
        }
		boolean omni = getBooleanSetting("omni", false);
		boolean moving;
		if (omni) {
			Vec2 movement = mc.player.input.getMoveVector();
			moving = movement.x * movement.x + movement.y * movement.y > 1.0E-4F;
		} else {
			moving = mc.player.input.hasForwardImpulse();
		}
		boolean allowedUsing = getBooleanSetting("whileUsing", false) || !mc.player.isUsingItem();
		boolean allowedSneaking = getBooleanSetting("whileSneaking", false) || !mc.player.isShiftKeyDown();
		// Vanilla's own rule, read from LocalPlayer in the 26.2 jar: it stops a run sprint on
		// `horizontalCollision && !minorHorizontalCollision`, so brushing a wall does not count.
		// Testing the bare flag dropped the player to a walk on every tick they touched a corridor
		// wall, which is the opposite of the "respects vanilla sprint eligibility" this promises.
		boolean blocked = mc.player.horizontalCollision && !mc.player.minorHorizontalCollision;
		boolean shouldSprint = moving
				&& !blocked
				&& allowedUsing
				&& allowedSneaking
				&& mc.player.canSprint();

		// Only ever hands back a sprint it started itself. Writing the state unconditionally meant
		// that sneaking, or touching a wall, cancelled a sprint the player was holding by hand -
		// taking an input away from them, which is the one thing PlayerInputOverrides exists to say
		// this client does not do.
		if (shouldSprint) {
			if (!mc.player.isSprinting()) {
				mc.player.setSprinting(true);
				appliedSprint = true;
			}
		} else if (appliedSprint) {
			mc.player.setSprinting(false);
			appliedSprint = false;
		}
	}

	@Override
	public void onDisable() {
		// Undoes exactly what it did and nothing else. appliedSprint is only ever set when the module
		// turned a sprint on from off, so switching it back off is the whole of the restoration -
		// and a sprint the player was holding themselves is left running, because it was never ours.
		if (capturedPlayer != null && appliedSprint) {
			capturedPlayer.setSprinting(false);
		}
		appliedSprint = false;
		capturedPlayer = null;
	}
}
