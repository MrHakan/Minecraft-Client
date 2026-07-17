package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class Fullbright extends Module {

	public Fullbright() {
		super("Fullbright", Category.RENDER, "Lights up the whole world (client-side night vision)");
	}

	@Override
	public void onUpdate() {
		if (!mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
			mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, StatusEffectInstance.INFINITE, 0, false, false, false));
		}
	}

	@Override
	public void onDisable() {
		if (mc.player != null) {
			mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
		}
	}
}
