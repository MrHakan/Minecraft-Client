package me.mrhakan.agalarhack.services;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.scanning.ScanScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Bounded, tick-scheduled entity discovery shared by the visual modules.
 *
 * <p>Every ESP-style module needs the same thing: walk the render list on the shared budget rather
 * than during rendering, keep only the nearest results, and never publish a snapshot that refers to
 * a world or player that has since been replaced. Written once here so the consumers cannot drift
 * apart on the caps or the staleness checks.
 *
 * <p>The walk itself is shared. Each module used to walk the whole list on its own and pay a unit per
 * entity, so five of them truncated each other on a busy server; now one walk per tick serves all of
 * them and an entity costs one unit however many modules look at it. See
 * {@link me.mrhakan.agalarhack.services.scanning.SharedEntityWalk}.
 */
public final class EntityDiscovery {
    private EntityDiscovery() { }

    /**
     * Ceiling on entities inspected in one shared walk.
     *
     * <p>The same figure as the balanced profile's whole entity allowance, and now paid once per
     * entity rather than once per consumer, so every subscriber sees the same prefix of the list
     * instead of whichever share the scheduler's rotation left it that tick.
     */
    public static final int MAX_OBSERVATIONS = 4096;

    /**
     * @param select returns the value to keep for an entity, or null to skip it; only called for
     *               entities already inside {@code range}
     * @param sink   receives an immutable snapshot once the walk ends, or an empty list if the
     *               world or player changed while it was running
     */
    public static <T> void offer(Module owner, ScannerService scanners, ScanScheduler.Priority priority,
            Minecraft client, int maximumResults, double range,
            Function<Entity, T> select, Consumer<List<T>> sink) {
        if (client.level == null || client.player == null) { sink.accept(List.of()); return; }
        scanners.offerEntities(owner, priority, maximumResults, range, select, sink);
    }
}
