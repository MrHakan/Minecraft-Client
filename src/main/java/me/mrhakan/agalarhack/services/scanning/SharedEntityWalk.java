package me.mrhakan.agalarhack.services.scanning;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import me.mrhakan.agalarhack.services.NearestCandidates;

/**
 * One bounded walk over the entity list per tick, shared by every consumer that asked for one.
 *
 * <p>Each ESP-style module used to walk the render list on its own, paying one entity unit per
 * entity from the same per-tick pool. Five of them on a server with a few hundred rendered entities
 * spent the whole balanced allowance between them, and since the scheduler rotates task order every
 * tick, which module came up short changed from tick to tick. Here the list is walked once, each
 * entity costs one unit, and it is offered to every subscriber whose range covers it.
 *
 * <p>Subscriptions follow the scheduler's "re-offer each tick" rule. {@link #start} consumes them,
 * so a module that stops subscribing stops receiving results, and nothing from a disabled module can
 * survive into a later tick. A subscriber whose filter throws is dropped and reported on its own;
 * the walk carries on for the others.
 *
 * <p>Kept free of Minecraft types so the sharing, ranges, caps and failure isolation are tested
 * directly; the scanner service supplies the entity list and the distance and id functions.
 */
public final class SharedEntityWalk<K, E> {
    private final int maximumObservations;
    private final BiConsumer<K, RuntimeException> failure;
    private final Map<K, Subscription<K, E, ?>> subscriptions = new LinkedHashMap<>();
    /** The pass in progress, so a cancellation during it still stops that subscriber's result. */
    private List<Subscription<K, E, ?>> active = List.of();

    /**
     * @param maximumObservations entities inspected per walk at most
     * @param failure             told which subscriber's filter threw
     */
    public SharedEntityWalk(int maximumObservations, BiConsumer<K, RuntimeException> failure) {
        if (maximumObservations < 1) throw new IllegalArgumentException("Invalid observation limit");
        this.maximumObservations = maximumObservations;
        this.failure = Objects.requireNonNull(failure);
    }

    /**
     * Adds or replaces the owner's request for the next walk.
     *
     * @param select returns the value to keep for an entity, or null to skip it; only called for
     *               entities inside {@code range}
     * @param sink   receives the nearest selected values once the walk ends, nearest first, or an
     *               empty list if the world or player changed while it was running
     */
    public <T> void subscribe(K owner, ScanScheduler.Priority priority, int maximumResults, double range,
            Function<E, T> select, Consumer<List<T>> sink) {
        Objects.requireNonNull(owner);
        subscriptions.put(owner, new Subscription<>(owner, Objects.requireNonNull(priority),
                new NearestCandidates<>(maximumResults), range * range,
                Objects.requireNonNull(select), Objects.requireNonNull(sink)));
    }

    /** Forgets the owner's request, including one already part of a walk in progress. */
    public void cancel(K owner) {
        var pending = subscriptions.remove(owner);
        if (pending != null) pending.cancelled = true;
        for (var running : active) if (running.owner.equals(owner)) running.cancelled = true;
    }

    public void clear() {
        subscriptions.clear();
        for (var running : active) running.cancelled = true;
        active = List.of();
    }

    public boolean isEmpty() {
        return subscriptions.isEmpty();
    }

    /** The most urgent priority any subscriber asked for, so sharing never demotes one of them. */
    public ScanScheduler.Priority priority() {
        ScanScheduler.Priority urgent = ScanScheduler.Priority.BACKGROUND;
        for (var subscription : subscriptions.values()) {
            if (subscription.priority.ordinal() < urgent.ordinal()) urgent = subscription.priority;
        }
        return urgent;
    }

    /** The furthest any current subscriber looks, so entities beyond it can be left out of the walk. */
    public double maximumRange() {
        double furthest = 0;
        for (var subscription : subscriptions.values()) furthest = Math.max(furthest, subscription.rangeSquared);
        return Math.sqrt(furthest);
    }

    /** The most entities one walk inspects. */
    public int maximumObservations() {
        return maximumObservations;
    }

    /** Steps a walk can take: one per observation, plus the one that finds the end. */
    public int maximumSteps() {
        return maximumObservations + 1;
    }

    /**
     * Takes every current subscription and returns the scheduler task that serves them.
     *
     * @param entities        this tick's entity list
     * @param distanceSquared distance from the player
     * @param id              tie-breaker for equally distant entities, so results are stable
     * @param stillValid      false once the world or player the walk started in has been replaced
     */
    public ScanScheduler.Task start(Iterator<E> entities, ToDoubleFunction<E> distanceSquared, ToIntFunction<E> id,
            BooleanSupplier stillValid) {
        List<Subscription<K, E, ?>> walk = List.copyOf(subscriptions.values());
        subscriptions.clear();
        active = walk;
        int[] considered = { 0 };
        boolean[] finished = { false };
        return budget -> {
            if (finished[0]) return ScanScheduler.Result.DONE;
            if (!stillValid.getAsBoolean()) {
                finish(walk, finished, true);
                return ScanScheduler.Result.DONE;
            }
            if (considered[0] >= maximumObservations || !entities.hasNext()) {
                finish(walk, finished, false);
                return ScanScheduler.Result.DONE;
            }
            if (!budget.take(0, 0, 1)) {
                // Out of budget is not the same as having walked the list, so this reports BLOCKED like
                // every other scanner. What was found is still published - the walk cannot resume, as
                // the next tick starts a fresh one - but it is a prefix of the list, not all of it.
                finish(walk, finished, false);
                return ScanScheduler.Result.BLOCKED;
            }
            considered[0]++;
            E entity = entities.next();
            double distance = distanceSquared.applyAsDouble(entity);
            int entityId = -1;
            boolean identified = false;
            for (var subscription : walk) {
                if (subscription.cancelled || distance > subscription.rangeSquared) continue;
                if (!identified) { entityId = id.applyAsInt(entity); identified = true; }
                try {
                    subscription.offer(entity, distance, entityId);
                } catch (RuntimeException error) {
                    subscription.cancelled = true;
                    try { failure.accept(subscription.owner, error); }
                    catch (RuntimeException reporting) { error.addSuppressed(reporting); }
                }
            }
            return ScanScheduler.Result.MORE;
        };
    }

    private void finish(List<Subscription<K, E, ?>> walk, boolean[] finished, boolean invalidated) {
        finished[0] = true;
        if (active == walk) active = List.of();
        // The walk list is immutable, so a sink that toggles its own module - which cancels through
        // this class - only flips a flag and cannot disturb this loop.
        for (var subscription : walk) {
            if (subscription.cancelled) continue;
            try {
                subscription.publish(invalidated);
            } catch (RuntimeException error) {
                try { failure.accept(subscription.owner, error); }
                catch (RuntimeException reporting) { error.addSuppressed(reporting); }
            }
        }
    }

    private static final class Subscription<K, E, T> {
        final K owner;
        final ScanScheduler.Priority priority;
        final NearestCandidates<T> nearest;
        final double rangeSquared;
        final Function<E, T> select;
        final Consumer<List<T>> sink;
        boolean cancelled;

        Subscription(K owner, ScanScheduler.Priority priority, NearestCandidates<T> nearest, double rangeSquared,
                Function<E, T> select, Consumer<List<T>> sink) {
            this.owner = owner; this.priority = priority; this.nearest = nearest; this.rangeSquared = rangeSquared;
            this.select = select; this.sink = sink;
        }

        void offer(E entity, double distanceSquared, int id) {
            T selected = select.apply(entity);
            if (selected != null) nearest.add(selected, distanceSquared, id);
        }

        void publish(boolean invalidated) {
            sink.accept(invalidated ? List.of() : nearest.snapshot());
        }
    }
}
