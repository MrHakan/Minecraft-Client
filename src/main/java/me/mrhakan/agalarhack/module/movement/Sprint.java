package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

public class Sprint extends Module {

    private boolean lastAppliedSprint;
    private boolean hasAppliedState;

    public Sprint() {
        super("Sprint", Category.MOVEMENT, "Automatically sprints with configurable directional and movement safeguards");
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
            hasAppliedState = false;
            return;
        }

        boolean omni = getBooleanSetting("omni", false);
        boolean moving = omni
                ? mc.player.input.getMoveVector().lengthSqr() > 1.0E-4
                : mc.player.input.hasForwardImpulse();
        boolean allowedUsing = getBooleanSetting("whileUsing", false) || !mc.player.isUsingItem();
        boolean allowedSneaking = getBooleanSetting("whileSneaking", false) || !mc.player.isShiftKeyDown();
        boolean shouldSprint = moving
                && !mc.player.horizontalCollision
                && allowedUsing
                && allowedSneaking
                && mc.player.canSprint();

        // Avoid repeatedly writing the same sprint state every client tick.
        if (!hasAppliedState || lastAppliedSprint != shouldSprint || mc.player.isSprinting() != shouldSprint) {
            mc.player.setSprinting(shouldSprint);
            lastAppliedSprint = shouldSprint;
            hasAppliedState = true;
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }
        hasAppliedState = false;
        lastAppliedSprint = false;
    }

    @Override
    public void onDisconnect() {
        hasAppliedState = false;
        lastAppliedSprint = false;
    }
}
