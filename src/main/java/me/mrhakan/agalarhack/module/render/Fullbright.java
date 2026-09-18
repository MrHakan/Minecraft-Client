package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class Fullbright extends Module {

    private static final int REFRESH_THRESHOLD_TICKS = 220;
    private static final int EFFECT_DURATION_TICKS = 360;

    private boolean appliedNightVision;

    public Fullbright() {
        super("Fullbright", Category.RENDER, "Lights up the whole world with efficient client-side night vision");
    }

    @Override
    public void onUpdate() {
        if (mc.player == null) {
            return;
        }

        MobEffectInstance current = mc.player.getEffect(MobEffects.NIGHT_VISION);
        if (current != null && !appliedNightVision) {
            // Respect real potion/beacon effects instead of replacing them.
            return;
        }

        if (current == null || current.getDuration() <= REFRESH_THRESHOLD_TICKS) {
            mc.player.addEffect(new MobEffectInstance(
                    MobEffects.NIGHT_VISION,
                    EFFECT_DURATION_TICKS,
                    0,
                    false,
                    false,
                    false));
            appliedNightVision = true;
        }
    }

    @Override
    public void onDisable() {
        // Only remove an effect that Fullbright itself supplied.
        if (mc.player != null && appliedNightVision) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
        }
        appliedNightVision = false;
    }

    @Override
    public void onDisconnect() {
        appliedNightVision = false;
    }
}
