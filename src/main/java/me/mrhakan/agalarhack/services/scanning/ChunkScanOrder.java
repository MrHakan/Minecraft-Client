package me.mrhakan.agalarhack.services.scanning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Stable nearest-chunk-first order within a bounded horizontal radius. */
public final class ChunkScanOrder {
    public record Chunk(int x, int z) { }
    private ChunkScanOrder() { }
    public static List<Chunk> around(int x, int z, int range) {
        if (range < 0 || range > 160) throw new IllegalArgumentException("Invalid chunk scan range");
        int minX = Math.subtractExact(x, range) >> 4, maxX = Math.addExact(x, range) >> 4;
        int minZ = Math.subtractExact(z, range) >> 4, maxZ = Math.addExact(z, range) >> 4;
        var result = new ArrayList<Chunk>();
        for (int cx = minX; cx <= maxX; cx++) for (int cz = minZ; cz <= maxZ; cz++) result.add(new Chunk(cx, cz));
        int centerX = x >> 4, centerZ = z >> 4;
        result.sort(Comparator.comparingInt(chunk -> square(chunk.x - centerX) + square(chunk.z - centerZ)));
        return List.copyOf(result);
    }
    private static int square(int value) { return value * value; }
}
