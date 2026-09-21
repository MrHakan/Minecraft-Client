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

    /**
     * Ceiling on entities inspected in one pass.
     *
     * <p>It is <strong>not</strong> independent of the shared budget, whatever an earlier comment
     * here claimed: it is the same figure as the balanced profile's whole entity allowance, and the
     * five consumers each pay a unit per entity they look at out of that one pool. Past roughly
     * eight hundred rendered entities they therefore start truncating each other, and the scheduler
     * rotates task order every tick, so which of them truncates changes from tick to tick.
     *
     * <p>That is bounded scanning working as designed rather than a fault, but it is a real ceiling
     * on a busy server and the honest place to write it down is here. A single shared walk feeding
     * all five would remove it; that is a larger change than this comment.
     */
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
            if (considered[0] >= MAX_OBSERVATIONS || !iterator.hasNext()) {
                sink.accept(nearest.snapshot());
                return ScanScheduler.Result.DONE;
            }
            if (!budget.take(0, 0, 1)) {
                // Out of budget, which is not the same thing as having walked the list, and every
                // other scanner here says so by returning BLOCKED. What was found is still published
                // rather than dropped - the pass cannot resume, because the next tick starts from a
                // fresh iterator - but it is a prefix of the entity list, not all of it.
                sink.accept(nearest.snapshot());
                return ScanScheduler.Result.BLOCKED;
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
