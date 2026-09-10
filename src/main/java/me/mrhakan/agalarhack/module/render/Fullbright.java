package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class Fullbright extends Module {

	private boolean appliedNightVision;

	public Fullbright() {
		super("Fullbright", Category.RENDER, "Lights up the whole world (client-side night vision)");
	}

	@Override
	public void onUpdate() {
		if (!mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
			mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
			appliedNightVision = true;
		}
	}

	@Override
	public void onDisable() {
		// Do not remove a potion/beacon effect that was already present before
		// Fullbright supplied its own client-side night vision.
		if (mc.player != null && appliedNightVision) {
			mc.player.removeEffect(MobEffects.NIGHT_VISION);
		}
		appliedNightVision = false;
	}
}
