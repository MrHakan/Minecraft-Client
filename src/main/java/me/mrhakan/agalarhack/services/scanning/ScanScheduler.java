package me.mrhakan.agalarhack.services.scanning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Cooperative client-tick scheduler. Every task step must perform only a bounded unit of work. */
public final class ScanScheduler<K> {
    public enum Priority { NEAR, FOCUSED, BACKGROUND }
    public enum Result { MORE, DONE, BLOCKED }
    @FunctionalInterface public interface Task { Result step(Budget budget); }
    public record Usage(int blocks, int chunkLookups, int entities, int steps) { }

    public static final class Budget {
        private final int maxBlocks, maxChunks, maxEntities;
        private int blocks, chunks, entities;
        public Budget(int blocks, int chunks, int entities) {
            if (blocks < 0 || chunks < 0 || entities < 0) throw new IllegalArgumentException("Negative scan budget");
            maxBlocks = blocks; maxChunks = chunks; maxEntities = entities;
        }
        /** Atomic acquisition: a failed request never partially consumes the remaining budget. */
        public boolean take(int blockCount, int chunkCount, int entityCount) {
            if (blockCount < 0 || chunkCount < 0 || entityCount < 0) throw new IllegalArgumentException("Negative scan cost");
            if (blockCount > maxBlocks - blocks || chunkCount > maxChunks - chunks || entityCount > maxEntities - entities) return false;
            blocks += blockCount; chunks += chunkCount; entities += entityCount;
            return true;
        }
        /** Total units consumed so far, used to tell a working step from a spinning one. */
        int spent() { return blocks + chunks + entities; }

        /**
         * The tightest remaining allowance across the dimensions this budget actually grants.
         *
         * <p>A dimension whose maximum is zero is skipped: nothing can ever be taken from it, so it
         * says nothing about how scarce the budget is. With every dimension zero there is no work to
         * share and the answer is zero.
         */
        int scarcestRemaining() {
            int tightest = Integer.MAX_VALUE;
            if (maxBlocks > 0) tightest = Math.min(tightest, maxBlocks - blocks);
            if (maxChunks > 0) tightest = Math.min(tightest, maxChunks - chunks);
            if (maxEntities > 0) tightest = Math.min(tightest, maxEntities - entities);
            return tightest == Integer.MAX_VALUE ? 0 : tightest;
        }
    }

    /**
     * How many consecutive steps may consume nothing before the run is stopped.
     *
     * <p>This, rather than a flat step count, is what guards the tick. A task that spends budget is
     * already bounded by the budget; the only way to run away is to keep returning MORE without
     * spending, and that is what this catches. The previous guard was a flat 16384 steps, which
     * bounded the honest tasks too: the "high" profile grants 28,000 blocks, so a scanner spending
     * one block per step - BlockESP and the entity walk both do - stopped at 16,384 whatever the
     * setting said, and choosing "high" bought nothing over "balanced".
     */
    public static final int MAX_IDLE_STEPS = 4096;
    private record Request(Priority priority, int steps, Task task) { }
    private final Map<K, Request> pending = new LinkedHashMap<>();
    private final BiConsumer<K, RuntimeException> failure;
    private Usage lastUsage = new Usage(0, 0, 0, 0);
    private int round;

    public ScanScheduler(BiConsumer<K, RuntimeException> failure) { this.failure = Objects.requireNonNull(failure); }

    /** Re-offer each tick. Work from disabled modules must not survive into a later tick. */
    public void offer(K owner, Priority priority, int maximumSteps, Task task) {
        Objects.requireNonNull(owner); Objects.requireNonNull(priority); Objects.requireNonNull(task);
        if (maximumSteps < 1 || maximumSteps > 16384) throw new IllegalArgumentException("Invalid step allowance");
        if (!pending.containsKey(owner) && pending.size() >= 64) throw new IllegalStateException("Too many scan tasks");
        pending.put(owner, new Request(priority, maximumSteps, task));
    }
    public void cancel(K owner) { pending.remove(owner); }
    public void clear() { pending.clear(); lastUsage = new Usage(0, 0, 0, 0); }
    public Usage lastUsage() { return lastUsage; }

    /** How many consecutive steps a priority may take per round when the budget is not scarce. */
    private static int priorityQuantum(Priority priority) {
        return switch (priority) { case NEAR -> 64; case FOCUSED -> 32; case BACKGROUND -> 16; };
    }

    /**
     * The most steps one task may take this round without being able to starve a peer of budget.
     *
     * <p>The per-priority quantum alone is a fairness guarantee only while the budget is large
     * relative to it. The rotation above spreads the advantage of going first across ticks, which is
     * enough when a quantum cannot exhaust the tick - with 4,000 blocks even on the "low" profile,
     * a 16-step background quantum is a two-hundred-and-fiftieth of the budget. Chunk lookups are
     * the dimension where that stops being true: "low" grants 24 of them against that same quantum
     * of 16, so one task walking chunks could take two thirds of the tick's allowance in a single
     * turn and leave the scanners behind it nothing at all.
     *
     * <p>Dividing the scarcest remaining allowance between the tasks that still have work bounds
     * that, and costs nothing when the budget is roomy, where the per-priority quantum stays the
     * binding limit. Never less than one step, or a nearly spent budget would stop the run early
     * rather than letting the last taker find out it is empty.
     */
    private static int fairShare(Budget budget, java.util.List<?> work, int[] remaining) {
        int active = 0;
        for (int index = 0; index < work.size(); index++) if (remaining[index] > 0) active++;
        if (active <= 1) return Integer.MAX_VALUE;
        return Math.max(1, budget.scarcestRemaining() / active);
    }

    public void run(int blocks, int chunks, int entities) {
        Budget budget = new Budget(blocks, chunks, entities);
        // Derived from the budget rather than fixed, so the two ceilings cannot contradict each
        // other. The idle allowance is the slack a well-behaved task never needs.
        final long stepCeiling = (long) blocks + chunks + entities + MAX_IDLE_STEPS;
        int idleSteps = 0;
        var work = new ArrayList<>(pending.entrySet());
        // Rotate equal-priority entry order, so a tiny shared budget cannot permanently starve a peer.
        if (!work.isEmpty()) java.util.Collections.rotate(work, Math.floorMod(round++, work.size()));
        work.sort(Comparator.comparing(entry -> entry.getValue().priority()));
        int[] remaining = work.stream().mapToInt(entry -> entry.getValue().steps()).toArray();
        int totalSteps = 0;
        boolean progress = true;
        try {
            while (progress && totalSteps < stepCeiling && idleSteps < MAX_IDLE_STEPS) {
                progress = false;
                for (int index = 0; index < work.size() && totalSteps < stepCeiling && idleSteps < MAX_IDLE_STEPS; index++) {
                    var entry = work.get(index);
                    Request request = entry.getValue();
                    if (remaining[index] == 0 || pending.get(entry.getKey()) != request) continue;
                    // Earlier priorities receive more work per round, while background tasks still get a turn.
                    int quantum = Math.min(priorityQuantum(request.priority()), fairShare(budget, work, remaining));
                    for (int i = 0; i < quantum && remaining[index] > 0 && totalSteps < stepCeiling && idleSteps < MAX_IDLE_STEPS; i++) {
                        remaining[index]--; totalSteps++; progress = true;
                        try {
                            int before = budget.spent();
                            Result result = Objects.requireNonNull(request.task().step(budget));
                            idleSteps = budget.spent() == before ? idleSteps + 1 : 0;
                            if (result != Result.MORE) { remaining[index] = 0; break; }
                        } catch (RuntimeException error) {
                            remaining[index] = 0;
                            try { failure.accept(entry.getKey(), error); }
                            catch (RuntimeException reporting) { error.addSuppressed(reporting); }
                            break;
                        }
                    }
                }
            }
        } finally {
            pending.clear();
            lastUsage = new Usage(budget.blocks, budget.chunks, budget.entities, totalSteps);
        }
    }
}
