package me.mrhakan.agalarhack.mixin;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import me.mrhakan.agalarhack.services.PacketRates;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts the packets actually crossing the wire.
 *
 * <p>Nothing in Fabric 26.2 reports this, and it is the one network figure the client genuinely
 * knows rather than infers. Both injections are at HEAD and do nothing but a counter increment: they
 * never cancel, never read the packet, and never touch the connection.
 *
 * <p>These run on the <b>netty thread</b>, not the client thread, which is why they call into static
 * methods holding thread-safe counters instead of reaching for a service. {@code channelRead0} runs
 * for every packet received, so the cost when nobody is watching has to be two volatile reads and
 * nothing more — and that is what {@link PacketRates} is shaped for.
 *
 * <p>Outbound is counted on the three-argument {@code send}, which is where both other overloads
 * end up in 26.2. Counting on the one-argument version instead would miss everything sent through
 * the others, and counting on more than one would count the same packet twice.
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"))
    private void agalarhack$countInbound(ChannelHandlerContext context, Packet<?> packet, CallbackInfo info) {
        PacketRates.countInbound();
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"))
    private void agalarhack$countOutbound(Packet<?> packet, ChannelFutureListener listener,
                                          boolean flush, CallbackInfo info) {
        PacketRates.countOutbound();
    }
}
