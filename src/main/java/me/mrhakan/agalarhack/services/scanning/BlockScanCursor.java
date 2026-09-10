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
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public void advance() {
        if (x < endX) { x++; return; }
        x = startX;
        if (z < endZ) { z++; return; }
        z = startZ;
        if (y < maximumY) { y++; return; }
        skipChunk();
    }
    public void skipChunk() {
        chunkIndex = (chunkIndex + 1) % chunks.size();
        if (chunkIndex == 0) cycle++;
        startChunk();
    }
}
