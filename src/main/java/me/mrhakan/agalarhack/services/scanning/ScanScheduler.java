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
    }
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

    public void run(int blocks, int chunks, int entities) {
        Budget budget = new Budget(blocks, chunks, entities);
        var work = new ArrayList<>(pending.entrySet());
        // Rotate equal-priority entry order, so a tiny shared budget cannot permanently starve a peer.
        if (!work.isEmpty()) java.util.Collections.rotate(work, Math.floorMod(round++, work.size()));
        work.sort(Comparator.comparing(entry -> entry.getValue().priority()));
        int[] remaining = work.stream().mapToInt(entry -> entry.getValue().steps()).toArray();
        int totalSteps = 0;
        boolean progress = true;
        try {
            while (progress && totalSteps < 16384) {
                progress = false;
                for (int index = 0; index < work.size() && totalSteps < 16384; index++) {
                    var entry = work.get(index);
                    Request request = entry.getValue();
                    if (remaining[index] == 0 || pending.get(entry.getKey()) != request) continue;
                    // Earlier priorities receive more work per round, while background tasks still get a turn.
                    int quantum = switch (request.priority()) { case NEAR -> 64; case FOCUSED -> 32; case BACKGROUND -> 16; };
                    for (int i = 0; i < quantum && remaining[index] > 0 && totalSteps < 16384; i++) {
                        remaining[index]--; totalSteps++; progress = true;
                        try {
                            Result result = Objects.requireNonNull(request.task().step(budget));
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
