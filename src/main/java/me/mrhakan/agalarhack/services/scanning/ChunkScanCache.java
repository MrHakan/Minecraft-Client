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
    private final LinkedHashMap<Long, Boolean> clean;

    public ChunkScanCache() { this(DEFAULT_CAPACITY); }

    public ChunkScanCache(int capacity) {
        this.capacity = Math.max(1, Math.min(8192, capacity));
        this.clean = new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                return size() > ChunkScanCache.this.capacity;
            }
        };
    }

    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /**
     * True when the chunk was fully scanned and nothing has invalidated it since.
     *
     * <p>Uses {@code get} rather than {@code containsKey} so a lookup counts as an access: a chunk
     * the scanner keeps revisiting stays cached in preference to one it has walked away from.
     */
    public boolean isClean(int chunkX, int chunkZ) {
        return clean.get(key(chunkX, chunkZ)) != null;
    }

    public void markClean(int chunkX, int chunkZ) {
        clean.put(key(chunkX, chunkZ), Boolean.TRUE);
    }

    /** @return true when the chunk was previously clean, so callers can skip redundant work */
    public boolean invalidate(int chunkX, int chunkZ) {
        return clean.remove(key(chunkX, chunkZ)) != null;
    }

    public void clear() { clean.clear(); }

    public int size() { return clean.size(); }

    public int capacity() { return capacity; }
}
