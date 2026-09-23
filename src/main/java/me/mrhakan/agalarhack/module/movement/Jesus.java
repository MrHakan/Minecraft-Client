package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public class Jesus extends Module {

	public Jesus() {
		super("Jesus", Category.MOVEMENT,
				"Rises toward the water surface; lava uses a vertical lift; sneak to dive");
	}

	@Override
	public void selfSettings() {
		addBooleanSetting("water", true, "Apply buoyancy in water");
		addBooleanSetting("lava", true, "Apply buoyancy in lava");
		addNumberSetting("verticalSpeed", 0.1, 0.02, 0.3,
				"Maximum vertical correction toward the water surface or through lava");
	}

	@Override
	public void onUpdate() {
		if (mc.player == null || mc.player.isShiftKeyDown()) {
			return;
		}

		Vec3 velocity = mc.player.getDeltaMovement();
		double maxCorrection = getNumberSetting("verticalSpeed", 0.1);
		if (mc.player.isInWater() && getBooleanSetting("water", true) && mc.level != null) {
			double surfaceY = waterSurfaceY(mc.level, mc.player.blockPosition());
			if (Double.isFinite(surfaceY)) {
				double error = surfaceY - mc.player.getY();
				// Correct toward the measured fluid surface and damp existing vertical motion. The
				// small gravity allowance keeps the player near the surface instead of sinking a
				// fraction of a block between corrections.
				double correction = Mth.clamp(error * 0.8 - velocity.y * 0.25 + 0.04,
						-maxCorrection, maxCorrection);
				mc.player.setDeltaMovement(velocity.x, correction, velocity.z);
			}
			return;
		}
		if (mc.player.isInLava() && getBooleanSetting("lava", true)) {
			mc.player.setDeltaMovement(velocity.x, maxCorrection, velocity.z);
		}
	}

	/** Returns the top of a contiguous water column beginning at the player's current block. */
	public static double waterSurfaceY(BlockGetter world, BlockPos start) {
		if (world == null || start == null) return Double.NaN;
		BlockPos.MutableBlockPos cursor = start.mutable();
		double surface = Double.NaN;
		for (int depth = 0; depth < 64; depth++) {
			FluidState fluid = world.getFluidState(cursor);
			if (!fluid.is(FluidTags.WATER)) break;
			surface = cursor.getY() + fluid.getHeight(world, cursor);
			cursor.move(Direction.UP);
		}
		return surface;
	}
}
