package me.mrhakan.agalarhack.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.mrhakan.agalarhack.module.render.CameraTweaks;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets CameraTweaks suppress the damage camera shake.
 *
 * <p>Vanilla has no option for this, and the shake is applied inside a private renderer method, so
 * cancelling that method is the only route. It affects the local camera transform only: nothing
 * about the player's position, rotation or packets changes.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void agalarhack$skipHurtBob(CameraRenderState state, PoseStack poseStack, CallbackInfo info) {
        if (CameraTweaks.suppressHurtCamera()) info.cancel();
    }
}
