package me.mrhakan.agalarhack.mixin;

import me.mrhakan.agalarhack.module.movement.SafeWalk;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes SafeWalk reuse vanilla's own edge protection instead of reimplementing it.
 *
 * <p>{@code isStayingOnGroundSurface} is the gate vanilla already consults before backing the player
 * off a ledge while sneaking. Forcing it true is therefore exactly the sneaking behaviour, with none
 * of the movement changes that actually sneaking would cause.
 *
 * <p>{@code Player} is common code, so the injection is restricted to the client's own player: the
 * integrated server's copy must keep vanilla behaviour.
 */
@Mixin(Player.class)
public abstract class PlayerEdgeMixin {
    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
    private void agalarhack$holdEdge(CallbackInfoReturnable<Boolean> info) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || (Object) this != client.player) return;
        if (SafeWalk.shouldHoldEdge()) info.setReturnValue(true);
    }
}
