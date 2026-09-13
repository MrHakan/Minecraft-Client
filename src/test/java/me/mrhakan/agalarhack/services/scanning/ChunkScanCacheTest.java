package me.mrhakan.agalarhack.services.scanning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkScanCacheTest {
    @Test void aChunkIsDirtyUntilItIsMarkedClean() {
        var cache = new ChunkScanCache();
        assertFalse(cache.isClean(3, -4));
        cache.markClean(3, -4);
        assertTrue(cache.isClean(3, -4));
    }

    @Test void invalidationReportsWhetherAnythingWasCached() {
        var cache = new ChunkScanCache();
        assertFalse(cache.invalidate(1, 1));
        cache.markClean(1, 1);
        assertTrue(cache.invalidate(1, 1));
        assertFalse(cache.isClean(1, 1));
    }

    @Test void negativeCoordinatesDoNotCollide() {
        var cache = new ChunkScanCache();
        cache.markClean(-1, 5);
        assertFalse(cache.isClean(5, -1));
        assertFalse(cache.isClean(-1, -1));
        assertTrue(cache.isClean(-1, 5));
    }

    @Test void keysAreUniqueAcrossSignCombinations() {
        long a = ChunkScanCache.key(-1, 0);
        long b = ChunkScanCache.key(0, -1);
        long c = ChunkScanCache.key(-1, -1);
        long d = ChunkScanCache.key(0, 0);
        assertEquals(4, java.util.Set.of(a, b, c, d).size());
    }

    @Test void evictionForgetsRatherThanWronglyReportingClean() {
        var cache = new ChunkScanCache(2);
        cache.markClean(0, 0);
        cache.markClean(1, 0);
        cache.markClean(2, 0);
        assertEquals(2, cache.size());
        assertFalse(cache.isClean(0, 0));
        assertTrue(cache.isClean(2, 0));
    }

    @Test void recentlyCheckedChunksSurviveEviction() {
        var cache = new ChunkScanCache(2);
        cache.markClean(0, 0);
        cache.markClean(1, 0);
        assertTrue(cache.isClean(0, 0));
        cache.markClean(2, 0);
        assertTrue(cache.isClean(0, 0), "access should refresh recency");
        assertFalse(cache.isClean(1, 0));
    }

    @Test void capacityIsBoundedInBothDirections() {
        assertEquals(1, new ChunkScanCache(0).capacity());
        assertEquals(1, new ChunkScanCache(-10).capacity());
        assertEquals(8192, new ChunkScanCache(999999).capacity());
    }

    @Test void clearDropsEverything() {
        var cache = new ChunkScanCache();
        cache.markClean(4, 4);
        cache.clear();
        assertEquals(0, cache.size());
        assertFalse(cache.isClean(4, 4));
    }
}
