package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Two ways to see in the dark, with different costs.
 *
 * <p><b>nightVision</b> applies a client-side night vision effect. It is the brighter of the two, but
 * it puts an effect on the player that the server never granted: it shows in the inventory effect
 * list and anything reading your effects locally sees it.
 *
 * <p><b>gamma</b> only turns the brightness slider up. It is dimmer and it does not reach true
 * darkness, but it adds nothing to the player and washes out no colours. Vanilla clamps the slider,
 * so this stays inside the range the game already allows rather than forcing a value through a mixin
 * — the brightness a player could have set by hand, set for them.
 */
public class Fullbright extends Module {

	private net.minecraft.client.player.LocalPlayer capturedPlayer;
    private MobEffectInstance appliedEffect;
    /** The player's own brightness, captured the moment before it is changed. */
    private Double originalGamma;
    private double appliedGamma;

	public Fullbright() {
		super("Fullbright", Category.RENDER, "Lights up the whole world, either by night vision or by raising the brightness slider");
	}

	@Override
	public void selfSettings() {
		addChoiceSetting("mode", "nightVision",
				"nightVision is brighter but adds an effect the server never gave you; gamma only moves the brightness slider",
				"nightVision", "gamma");
		addNumberSetting("gammaLevel", 1.0, 0.5, 1.0, "Brightness to use in gamma mode, within the range vanilla allows");
	}

	@Override
	public void onUpdate() {
        if (mc.player == null) return;
        if (capturedPlayer != mc.player) { releaseEffect(); capturedPlayer = mc.player; }
        if ("gamma".equals(getStringSetting("mode", "nightVision"))) {
            releaseEffect();
            applyGamma();
            return;
        }
        restoreGamma();
		if (!mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
			mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
			appliedEffect = mc.player.getEffect(MobEffects.NIGHT_VISION);
		}
	}

    /**
     * Captured once per episode, not per tick: re-reading it after the first change would capture
     * our own value and make the restore a no-op.
     */
    private void applyGamma() {
        double wanted = getNumberSetting("gammaLevel", 1.0);
        if (originalGamma == null) originalGamma = mc.options.gamma().get();
        // Also re-applied when the player moved the slider themselves, so the mode keeps its promise.
        if (mc.options.gamma().get() != wanted) mc.options.gamma().set(wanted);
        // set() clamps to the range vanilla accepts, so record what actually took effect rather than
        // what was asked for; the restore compares against this to tell our change from theirs.
        appliedGamma = mc.options.gamma().get();
    }

    /** Only undoes a change that is still ours, so a slider the player moved since is left alone. */
    private void restoreGamma() {
        if (originalGamma == null) return;
        if (mc.options.gamma().get() == appliedGamma) mc.options.gamma().set(originalGamma);
        originalGamma = null;
        appliedGamma = 0;
    }

    /** Only removes the exact effect object installed here, not a later replacement. */
    private void releaseEffect() {
        if (capturedPlayer != null && appliedEffect != null
                && capturedPlayer.getEffect(MobEffects.NIGHT_VISION) == appliedEffect
                && appliedEffect.getDuration() == MobEffectInstance.INFINITE_DURATION) {
            capturedPlayer.removeEffect(MobEffects.NIGHT_VISION);
        }
        appliedEffect = null;
    }

	@Override
	public void onDisable() {
        releaseEffect();
        restoreGamma();
        capturedPlayer = null;
	}

    /** The player entity is gone, but the brightness slider is not — it still has to go back. */
    @Override public void onDisconnect() { onDisable(); }
}
