package me.mrhakan.agalarhack.mixin;

import me.mrhakan.agalarhack.events.BlockUpdateHooks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client's only source of server-sent block updates.
 *
 * <p>Both injections are at TAIL, so the world already holds the new state when listeners run and
 * vanilla's own handling is never altered or cancelled. Minecraft moves these handlers onto the
 * client thread before the body executes, so listeners observe the same thread as every other
 * client event.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void agalarhack$onBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo info) {
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        BlockUpdateHooks.blockUpdated(self.getLevel(), packet.getPos(), packet.getBlockState());
    }

    /**
     * Totem activations and equipment breaks arrive as entity events and have no Fabric hook either.
     * TAIL again, so vanilla's own handling has already run.
     */
    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void agalarhack$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo info) {
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        var level = self.getLevel();
        if (level == null) return;
        BlockUpdateHooks.entityEvent(level, packet.getEntity(level), packet.getEventId());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void agalarhack$onSectionBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo info) {
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        BlockUpdateHooks.sectionUpdated(self.getLevel(), packet::runUpdates);
    }
}
