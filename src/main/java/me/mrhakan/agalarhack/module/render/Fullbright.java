package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class Fullbright extends Module {

	public Fullbright() {
		super("Fullbright", Category.RENDER, "Lights up the whole world (client-side night vision)");
	}

	@Override
	public void onUpdate() {
		if (!mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
			mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
		}
	}

	@Override
	public void onDisable() {
		if (mc.player != null) {
			mc.player.removeEffect(MobEffects.NIGHT_VISION);
		}
	}
}
