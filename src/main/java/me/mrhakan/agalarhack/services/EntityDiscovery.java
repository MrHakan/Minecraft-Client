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
 * a world or player that has since been replaced. Written once here so the three consumers cannot
 * drift apart on the caps or the staleness checks.
 */
public final class EntityDiscovery {
    private EntityDiscovery() { }

    /** Hard ceiling on entities inspected per tick, independent of the shared budget. */
    public static final int MAX_OBSERVATIONS = 4096;

    /**
     * @param select returns the value to keep for an entity, or null to skip it; only called for
     *               entities already inside {@code range}
     * @param sink   receives an immutable snapshot once the pass ends, or an empty list if the
     *               world or player changed while it was running
     */
    public static <T> void offer(Module owner, ScannerService scanners, ScanScheduler.Priority priority,
            Minecraft client, int maximumResults, double range,
            Function<Entity, T> select, Consumer<List<T>> sink) {
        var level = client.level;
        var player = client.player;
        if (level == null || player == null) { sink.accept(List.of()); return; }
        var iterator = level.entitiesForRendering().iterator();
        var nearest = new NearestCandidates<T>(maximumResults);
        double rangeSquared = range * range;
        int[] considered = { 0 };
        scanners.offer(owner, priority, MAX_OBSERVATIONS + 1, budget -> {
            // A replaced world or player invalidates everything gathered so far.
            if (client.level != level || client.player != player) {
                sink.accept(List.of());
                return ScanScheduler.Result.DONE;
            }
            if (considered[0] >= MAX_OBSERVATIONS || !iterator.hasNext() || !budget.take(0, 0, 1)) {
                sink.accept(nearest.snapshot());
                return ScanScheduler.Result.DONE;
            }
            considered[0]++;
            Entity entity = iterator.next();
            double distance = player.distanceToSqr(entity);
            if (distance <= rangeSquared) {
                T selected = select.apply(entity);
                if (selected != null) nearest.add(selected, distance, entity.getId());
            }
            return ScanScheduler.Result.MORE;
        });
    }
}
