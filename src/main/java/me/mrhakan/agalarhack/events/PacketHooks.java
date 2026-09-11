package me.mrhakan.agalarhack.events;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.services.ClientServices;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bridge between the packet mixin and the internal event bus.
 *
 * <p>Fabric API 26.2 exposes chunk, block-entity and player-break events, but nothing for
 * server-sent block updates, entity events or time updates, so the packet handlers are the only
 * honest producer for those. This class keeps the mixin trivial: it holds no state, resolves the bus
 * defensively, and never lets a listener failure escape into Minecraft's packet handling.
 */
public final class PacketHooks {
    private PacketHooks() { }

    /**
     * Individual updates stop after this many blocks in one packet and a single chunk-wide
     * invalidation is posted instead. A section packet can legally carry 4096 changes, and
     * dispatching that many events inside packet handling is not worth the extra precision.
     */
    public static final int MAX_INDIVIDUAL_UPDATES = 512;

    /** A section update packet, replayed through vanilla's own iteration over its changes. */
    @FunctionalInterface
    public interface SectionReplay {
        void forEach(BiConsumer<BlockPos, BlockState> action);
    }

    public static void blockUpdated(ClientLevel level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || state == null) return;
        post(bus -> bus.post(new ClientEvents.BlockUpdated(level, pos.immutable(), state)));
    }

    /** Server-sent entity events; the same bridge rules apply as for block updates. */
    public static void entityEvent(ClientLevel level, net.minecraft.world.entity.Entity entity, byte eventId) {
        if (level == null || entity == null) return;
        post(bus -> bus.post(new ClientEvents.EntityEventReceived(level, entity, eventId)));
    }

    /**
     * A world-time update. Vanilla sends these on a fixed server-tick cadence, which is what makes
     * their spacing usable as a tick-rate estimate - an estimate, never an authoritative figure.
     */
    public static void serverTime(long gameTime) {
        post(bus -> bus.post(new ClientEvents.ServerTimeUpdated(gameTime)));
    }

    public static void sectionUpdated(ClientLevel level, SectionReplay replay) {
        if (level == null || replay == null) return;
        post(bus -> replaySection(bus, level, replay));
    }

    /**
     * Single pass over the packet. Chunk coordinates come from the first change rather than from
     * the packet's section field, which avoids an accessor mixin for a value every change already
     * carries: a section packet never spans more than one chunk.
     */
    static void replaySection(EventBus bus, ClientLevel level, SectionReplay replay) {
        BlockUpdateBatch batch = new BlockUpdateBatch(MAX_INDIVIDUAL_UPDATES);
        replay.forEach((pos, blockState) -> {
            if (pos == null || blockState == null) return;
            if (batch.accept(pos.getX(), pos.getZ())) {
                bus.post(new ClientEvents.BlockUpdated(level, pos.immutable(), blockState));
            }
        });
        if (batch.overflowed()) {
            bus.post(new ClientEvents.ChunkBlocksInvalidated(level, batch.chunkX(), batch.chunkZ()));
        }
    }

    private static void post(Consumer<EventBus> action) {
        var registry = ClientServices.registry();
        EventBus bus = registry == null ? null : registry.find(EventBus.class).orElse(null);
        if (bus == null) return;
        try {
            action.accept(bus);
        } catch (RuntimeException failure) {
            // Never let client code break vanilla packet handling; the bus already isolates
            // individual listeners, so reaching here means the dispatch itself failed.
            AgalarHackClient.LOGGER.error("Block update dispatch failed", failure);
        }
    }
}
