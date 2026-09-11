package me.mrhakan.agalarhack.events;

/**
 * Decides how one packet's block changes are reported.
 *
 * <p>Changes are reported individually until the cap is reached, after which the whole chunk is
 * marked stale instead. A section packet may legally carry 4096 changes, and dispatching that many
 * events inside Minecraft's packet handling costs more than the extra precision is worth.
 *
 * <p>The rule is kept here, free of Minecraft types, so the cap and the chunk attribution can be
 * unit tested without bootstrapping a client.
 */
public final class BlockUpdateBatch {
    private final int maximum;
    private int count;
    private int chunkX;
    private int chunkZ;
    private boolean overflowed;

    public BlockUpdateBatch(int maximum) {
        this.maximum = Math.max(0, maximum);
    }

    /**
     * Records a change at the given block coordinates.
     *
     * @return true when this change should be reported on its own, false once the cap is exceeded
     */
    public boolean accept(int blockX, int blockZ) {
        if (count == 0) {
            chunkX = Math.floorDiv(blockX, 16);
            chunkZ = Math.floorDiv(blockZ, 16);
        }
        if (count == Integer.MAX_VALUE) return false;
        count++;
        if (count > maximum) {
            overflowed = true;
            return false;
        }
        return true;
    }

    /** True when at least one change was dropped and the chunk should be treated as stale instead. */
    public boolean overflowed() { return overflowed; }

    public int count() { return count; }

    /** Chunk of the first recorded change; a section packet never spans more than one chunk. */
    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
}
