package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;

public class Step extends Module {

	private static final double DEFAULT_STEP_HEIGHT = 0.6;

	public Step() {
		super("Step", Category.MOVEMENT, "Lets you walk up full blocks without jumping");
	}

	@Override
	public void selfSettings() {
		settings.addSetting("height", 1.0);
	}

	@Override
	public void onUpdate() {
		EntityAttributeInstance stepHeight = mc.player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT);
		if (stepHeight != null) {
			stepHeight.setBaseValue(getNumberSetting("height", 1.0));
		}
	}

	@Override
	public void onDisable() {
		if (mc.player != null) {
			EntityAttributeInstance stepHeight = mc.player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT);
			if (stepHeight != null) {
				stepHeight.setBaseValue(DEFAULT_STEP_HEIGHT);
			}
		}
	}
}
