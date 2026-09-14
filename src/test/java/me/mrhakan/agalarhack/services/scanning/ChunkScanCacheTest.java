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

    @Test void anOmittedMatchDoesNotSkipTheRestOfAnUnfinishedChunk() {
        var cache = new ChunkScanCache();
        cache.recordOmittedMatch(0, 0);
        assertFalse(cache.isClean(0, 0), "remaining blocks must still be visited for updates/removals");
        cache.markClean(0, 0);
        for (int tick = 0; tick < 1000; tick++) assertTrue(cache.isClean(0, 0));
        assertEquals(1, cache.size(), "static saturation must not create a growing backlog");
        assertEquals(1, cache.resumeOmitted());
        assertFalse(cache.isClean(0, 0));
        assertEquals(0, cache.resumeOmitted(), "one capacity event must not schedule repeated sweeps");
    }

    @Test void capacityRecoveryWakesOnlyChunksThatLostResults() {
        var cache = new ChunkScanCache();
        cache.markClean(0, 0);
        cache.markClean(1, 0);
        cache.recordOmittedMatch(1, 0); // block update or cap reduction in an already clean chunk
        assertTrue(cache.isClean(1, 0), "do not rescan until there is room");
        assertEquals(1, cache.resumeOmitted());
        assertTrue(cache.isClean(0, 0), "unaffected static chunks stay asleep");
        assertFalse(cache.isClean(1, 0));
        cache.markClean(1, 0); // recovered with no further omissions
        assertEquals(0, cache.resumeOmitted());
    }

    @Test void deferredRecordsShareTheCacheBoundAndWorldInvalidation() {
        var cache = new ChunkScanCache(2);
        for (int chunk = 0; chunk < 100; chunk++) {
            cache.recordOmittedMatch(chunk, -chunk);
            cache.markClean(chunk, -chunk);
            assertTrue(cache.size() <= 2);
        }
        assertFalse(cache.isClean(0, 0), "eviction makes the chunk dirty, never falsely complete");
        assertTrue(cache.invalidate(99, -99));
        assertFalse(cache.isClean(99, -99));
        cache.clear();
        assertEquals(0, cache.resumeOmitted());
        assertEquals(0, cache.size());
    }

    @Test void aCapacityChangeDuringAPassRetiresItsPendingOmission() {
        var cache = new ChunkScanCache();
        cache.recordOmittedMatch(2, 3);
        assertEquals(1, cache.resumeOmitted());
        // The caller restarts this chunk and supplies all its results; the old pending omission
        // must not survive completion and provoke another scan the next time capacity changes.
        cache.markClean(2, 3);
        assertTrue(cache.isClean(2, 3));
        assertEquals(0, cache.resumeOmitted());
    }

    @Test void deferredWorkStillSharesTheSchedulerBudgetWithAPeer() {
        var cache = new ChunkScanCache();
        cache.recordOmittedMatch(0, 0); cache.markClean(0, 0);
        var scheduler = new ScanScheduler<String>((owner, failure) -> fail(failure));
        int[] scan = {0}, peer = {0};
        for (int tick = 0; tick < 20; tick++) {
            if (tick == 10) cache.resumeOmitted();
            scheduler.offer("block", ScanScheduler.Priority.BACKGROUND, 100, budget -> {
                if (!budget.take(1, 0, 0)) return ScanScheduler.Result.BLOCKED;
                if (cache.isClean(0, 0)) return ScanScheduler.Result.DONE;
                scan[0]++;
                if (scan[0] == 4) { cache.markClean(0, 0); return ScanScheduler.Result.DONE; }
                return ScanScheduler.Result.MORE;
            });
            scheduler.offer("peer", ScanScheduler.Priority.BACKGROUND, 100, budget -> {
                if (!budget.take(1, 0, 0)) return ScanScheduler.Result.BLOCKED;
                peer[0]++;
                return ScanScheduler.Result.MORE;
            });
            int before = peer[0];
            scheduler.run(2, 0, 0);
            assertTrue(scheduler.lastUsage().blocks() <= 2);
            assertTrue(peer[0] > before, "recovering a capped scanner must leave its peer a turn");
        }
        assertEquals(4, scan[0], "one bounded recovery, then no more probes");
    }

}
