package me.mrhakan.agalarhack.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockUpdateBatchTest {
    @Test void reportsEveryChangeUpToTheCap() {
        var batch = new BlockUpdateBatch(3);
        assertTrue(batch.accept(0, 0));
        assertTrue(batch.accept(1, 0));
        assertTrue(batch.accept(2, 0));
        assertFalse(batch.overflowed());
        assertEquals(3, batch.count());
    }

    @Test void switchesToChunkInvalidationPastTheCap() {
        var batch = new BlockUpdateBatch(2);
        assertTrue(batch.accept(0, 0));
        assertTrue(batch.accept(1, 0));
        assertFalse(batch.accept(2, 0));
        assertFalse(batch.accept(3, 0));
        assertTrue(batch.overflowed());
    }

    @Test void chunkComesFromTheFirstChangeAndDoesNotDrift() {
        var batch = new BlockUpdateBatch(64);
        batch.accept(35, 20);
        batch.accept(32, 31);
        assertEquals(2, batch.chunkX());
        assertEquals(1, batch.chunkZ());
    }

    @Test void negativeCoordinatesFloorTowardsNegativeInfinity() {
        var batch = new BlockUpdateBatch(8);
        batch.accept(-1, -17);
        assertEquals(-1, batch.chunkX());
        assertEquals(-2, batch.chunkZ());
    }

    @Test void aZeroCapReportsNothingIndividually() {
        var batch = new BlockUpdateBatch(0);
        assertFalse(batch.accept(5, 5));
        assertTrue(batch.overflowed());
        assertEquals(0, batch.chunkX());
        assertEquals(0, batch.chunkZ());
    }

    @Test void anEmptyBatchNeverOverflows() {
        var batch = new BlockUpdateBatch(4);
        assertFalse(batch.overflowed());
        assertEquals(0, batch.count());
    }

    @Test void aNegativeCapIsTreatedAsZeroRatherThanRejectingEverythingSilently() {
        var batch = new BlockUpdateBatch(-5);
        assertFalse(batch.accept(0, 0));
        assertTrue(batch.overflowed());
    }
}
