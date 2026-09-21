package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class Step extends Module {
	private AttributeInstance capturedAttribute;
	private double previousStepHeight;
	private double appliedStepHeight = Double.NaN;

	public Step() {
		super("Step", Category.MOVEMENT, "Lets you walk up taller blocks while restoring the original step height when disabled");
	}
	@Override public void selfSettings() {
		addNumberSetting("height", 1.0, 0.6, 4.0, "Maximum automatic step height in blocks");
	}
	@Override public void onEnable() { captureCurrentAttribute(); }
	@Override public void onUpdate() {
		if (mc.player == null) return;
		AttributeInstance stepHeight = mc.player.getAttribute(Attributes.STEP_HEIGHT);
		if (stepHeight == null) return;
		if (capturedAttribute != stepHeight) {
			restoreCapturedAttribute();
			capturedAttribute = stepHeight;
			previousStepHeight = stepHeight.getBaseValue();
			appliedStepHeight = Double.NaN;
		}
		double configuredHeight = getNumberSetting("height", 1.0);
		if (Double.compare(appliedStepHeight, configuredHeight) != 0
				|| Double.compare(stepHeight.getBaseValue(), configuredHeight) != 0) {
			stepHeight.setBaseValue(configuredHeight);
			appliedStepHeight = configuredHeight;
		}
	}
	@Override public void onDisable() { restoreCapturedAttribute(); }
	private void captureCurrentAttribute() {
		if (mc.player == null) return;
		AttributeInstance stepHeight = mc.player.getAttribute(Attributes.STEP_HEIGHT);
		if (stepHeight != null) {
			capturedAttribute = stepHeight;
			previousStepHeight = stepHeight.getBaseValue();
			appliedStepHeight = Double.NaN;
		}
	}
	private void restoreCapturedAttribute() {
		if (capturedAttribute != null) {
			capturedAttribute.setBaseValue(previousStepHeight);
			capturedAttribute = null;
		}
		appliedStepHeight = Double.NaN;
	}
}
