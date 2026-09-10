package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class Fullbright extends Module {

	private net.minecraft.client.player.LocalPlayer capturedPlayer;
    private MobEffectInstance appliedEffect;

	public Fullbright() {
		super("Fullbright", Category.RENDER, "Lights up the whole world (client-side night vision)");
	}

	@Override
	public void onUpdate() {
        if (mc.player == null) return;
        if (capturedPlayer != mc.player) { onDisable(); capturedPlayer = mc.player; }
		if (!mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
			mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
			appliedEffect = mc.player.getEffect(MobEffects.NIGHT_VISION);
		}
	}

	@Override
	public void onDisable() {
        // Only remove the exact effect object installed by this module, not a later replacement.
        if (capturedPlayer != null && appliedEffect != null
                && capturedPlayer.getEffect(MobEffects.NIGHT_VISION) == appliedEffect
                && appliedEffect.getDuration() == MobEffectInstance.INFINITE_DURATION) {
            capturedPlayer.removeEffect(MobEffects.NIGHT_VISION);
        }
        capturedPlayer = null;
        appliedEffect = null;
	}
}
