package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.phys.Vec3;

public class Jesus extends Module {

	public Jesus() {
		super("Jesus", Category.MOVEMENT, "Keeps you buoyant in selected fluids; sneak to dive normally");
	}

	@Override
	public void selfSettings() {
		addBooleanSetting("water", true, "Apply buoyancy in water");
		addBooleanSetting("lava", true, "Apply buoyancy in lava");
		addNumberSetting("verticalSpeed", 0.1, 0.02, 0.3, "Upward velocity applied while inside a selected fluid");
	}

	@Override
	public void onUpdate() {
		if (mc.player == null || mc.player.isShiftKeyDown()) {
			return;
		}

		boolean activeFluid = (mc.player.isInWater() && getBooleanSetting("water", true))
				|| (mc.player.isInLava() && getBooleanSetting("lava", true));
		if (!activeFluid) {
			return;
		}

		Vec3 velocity = mc.player.getDeltaMovement();
		mc.player.setDeltaMovement(velocity.x, getNumberSetting("verticalSpeed", 0.1), velocity.z);
	}
}
