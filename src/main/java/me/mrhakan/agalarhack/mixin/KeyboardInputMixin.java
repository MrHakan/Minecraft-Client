package me.mrhakan.agalarhack.mixin;

import me.mrhakan.agalarhack.services.PlayerInputOverrides;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies module input requests at the only moment they survive.
 *
 * <p>{@code KeyboardInput.tick} rebuilds {@code keyPresses} from the keyboard and then derives the
 * movement vector from it; {@code LocalPlayer.aiStep} calls it and reads both a few lines later.
 * Modules run at the end of the previous client tick, so anything they wrote to {@code keyPresses}
 * was replaced here before vanilla ever looked at it. AutoWalk and Parkour both did exactly that and
 * both did nothing at all in a running game as a result, while ticking without complaint.
 *
 * <p>Injecting at {@code TAIL} puts the request in after the rebuild and before the read. The
 * movement vector has to be rebuilt too, because it was derived from the keys as they were a moment
 * ago - which is why this calls vanilla's own {@code calculateImpulse} through an accessor instead of
 * copying the arithmetic. A second implementation of it would be one more thing to get subtly wrong.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

    @Invoker("calculateImpulse")
    static float agalarhack$callCalculateImpulse(boolean positive, boolean negative) {
        throw new AssertionError("replaced by the mixin processor");
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void agalarhack$applyRequestedKeys(CallbackInfo info) {
        Input requested = PlayerInputOverrides.consume(keyPresses);
        if (requested == null || requested.equals(keyPresses)) {
            return;
        }
        keyPresses = requested;
        float forward = agalarhack$callCalculateImpulse(requested.forward(), requested.backward());
        float strafe = agalarhack$callCalculateImpulse(requested.left(), requested.right());
        moveVector = new Vec2(strafe, forward).normalized();
    }
}
