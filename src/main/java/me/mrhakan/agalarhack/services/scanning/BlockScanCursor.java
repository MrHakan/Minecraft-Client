package me.mrhakan.agalarhack.services.scanning;

import java.util.List;

/** Chunk-local traversal of a bounded box, nearest chunks first. No world access or per-step allocation. */
public final class BlockScanCursor {
    private List<ChunkScanOrder.Chunk> chunks = List.of();
    private int minimumX, maximumX, minimumY, maximumY, minimumZ, maximumZ;
    private long cycle;
    private int chunkIndex, x, y, z, startX, endX, startZ, endZ;

    public void reset(int centerX, int centerY, int centerZ, int horizontal, int vertical) {
        if (horizontal < 0 || horizontal > 64 || vertical < 0 || vertical > 48) throw new IllegalArgumentException("Invalid scan radius");
        minimumX = Math.subtractExact(centerX, horizontal); maximumX = Math.addExact(centerX, horizontal);
        minimumY = Math.subtractExact(centerY, vertical); maximumY = Math.addExact(centerY, vertical);
        minimumZ = Math.subtractExact(centerZ, horizontal); maximumZ = Math.addExact(centerZ, horizontal);
        chunks = ChunkScanOrder.around(centerX, centerZ, horizontal);
        chunkIndex = 0; cycle = 0;
        startChunk();
    }
    private void startChunk() {
        ChunkScanOrder.Chunk chunk = chunks.get(chunkIndex);
        startX = Math.max(minimumX, chunk.x() * 16); endX = Math.min(maximumX, chunk.x() * 16 + 15);
        startZ = Math.max(minimumZ, chunk.z() * 16); endZ = Math.min(maximumZ, chunk.z() * 16 + 15);
        x = startX; z = startZ; y = minimumY;
    }
    public long cycle() { return cycle; }
    /** Chunk the cursor is currently walking; valid immediately after reset. */
    public int chunkX() { return chunks.get(chunkIndex).x(); }
    public int chunkZ() { return chunks.get(chunkIndex).z(); }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    /**
     * Steps to the next position.
     *
     * @return true when the current chunk was exhausted and the cursor moved to the next one, so a
     *         caller can record that the chunk was scanned in full rather than abandoned part-way
     */
    public boolean advance() {
        if (x < endX) { x++; return false; }
        x = startX;
        if (z < endZ) { z++; return false; }
        z = startZ;
        if (y < maximumY) { y++; return false; }
        skipChunk();
        return true;
    }
    public void skipChunk() {
        chunkIndex = (chunkIndex + 1) % chunks.size();
        if (chunkIndex == 0) cycle++;
        startChunk();
    }
}
