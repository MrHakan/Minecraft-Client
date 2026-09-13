package me.mrhakan.agalarhack.services.scanning;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers which chunks a scanner has already finished and has no reason to look at again.
 *
 * <p>Without this every scanner cursor re-probes the same static blocks forever. Marking a chunk
 * clean once it has been fully traversed turns that into a one-off cost, and the block-update
 * events decide when it becomes interesting again.
 *
 * <p>The cache is bounded and evicts least-recently-marked entries. Eviction is deliberately the
 * safe direction: a forgotten chunk is simply rescanned, never wrongly treated as clean.
 */
public final class ChunkScanCache {
    /** Generous enough for the largest supported scan radius, small enough to stay bounded. */
    public static final int DEFAULT_CAPACITY = 1024;

    private final int capacity;
    private enum State { COMPLETE, OMITTED_PENDING, OMITTED_COMPLETE }
    private final LinkedHashMap<Long, State> clean;

    public ChunkScanCache() { this(DEFAULT_CAPACITY); }

    public ChunkScanCache(int capacity) {
        this.capacity = Math.max(1, Math.min(8192, capacity));
        this.clean = new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, State> eldest) {
                return size() > ChunkScanCache.this.capacity;
            }
        };
    }

    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /**
     * True when the chunk was fully scanned and can sleep until a world or capacity change.
     * Omitted matches wait for capacity rather than causing a continuous rescan at the result cap.
     *
     * <p>Uses {@code get} rather than {@code containsKey} so a lookup counts as an access: a chunk
     * the scanner keeps revisiting stays cached in preference to one it has walked away from.
     */
    public boolean isClean(int chunkX, int chunkZ) {
        State state = clean.get(key(chunkX, chunkZ));
        return state == State.COMPLETE || state == State.OMITTED_COMPLETE;
    }

    public void markClean(int chunkX, int chunkZ) {
        long key = key(chunkX, chunkZ);
        State previous = clean.get(key);
        clean.put(key, previous == State.OMITTED_PENDING || previous == State.OMITTED_COMPLETE
                ? State.OMITTED_COMPLETE : State.COMPLETE);
    }

    /** A matching block did not fit. A partially walked chunk must still finish its current pass. */
    public void recordOmittedMatch(int chunkX, int chunkZ) {
        long key = key(chunkX, chunkZ);
        State previous = clean.get(key);
        clean.put(key, previous == State.COMPLETE || previous == State.OMITTED_COMPLETE
                ? State.OMITTED_COMPLETE : State.OMITTED_PENDING);
    }

    /**
     * Capacity became available: wake only chunks with omitted matches, once. The same bounded map
     * holds both complete and deferred records; there is no growing secondary backlog.
     * The caller restarts its current chunk if it was already mid-pass when space became available.
     */
    public int resumeOmitted() {
        int before = clean.size();
        clean.values().removeIf(state -> state != State.COMPLETE);
        return before - clean.size();
    }

    /** @return true when any complete or deferred record was present */
    public boolean invalidate(int chunkX, int chunkZ) {
        return clean.remove(key(chunkX, chunkZ)) != null;
    }

    public void clear() { clean.clear(); }

    public int size() { return clean.size(); }

    public int capacity() { return capacity; }
}
