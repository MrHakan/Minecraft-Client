package me.mrhakan.agalarhack.services.scanning;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockScanCursorTest {
    @Test void coversClippedChunkEdgesAndNegativeCoordinatesExactlyOncePerCycle() {
        var cursor = new BlockScanCursor();
        cursor.reset(-15, 64, -1, 17, 2);
        var positions = new HashSet<String>();
        int firstX = cursor.x(), firstY = cursor.y(), firstZ = cursor.z();
        assertEquals(-1, firstX >> 4);
        assertEquals(-1, firstZ >> 4);
        for (int i = 0; i < 35 * 35 * 5; i++) {
            assertTrue(cursor.x() >= -32 && cursor.x() <= 2);
            assertTrue(cursor.y() >= 62 && cursor.y() <= 66);
            assertTrue(cursor.z() >= -18 && cursor.z() <= 16);
            assertTrue(positions.add(cursor.x() + ":" + cursor.y() + ":" + cursor.z()));
            cursor.advance();
        }
        assertEquals(1, cursor.cycle());
        assertEquals(firstX, cursor.x()); assertEquals(firstY, cursor.y()); assertEquals(firstZ, cursor.z());
    }
    @Test void completionIsReportedOnlyOnTheStepThatExhaustsTheChunk() {
        var cursor = new BlockScanCursor();
        // A single column inside one chunk: five Y positions, so only the fifth step completes it.
        cursor.reset(0, 64, 0, 0, 2);
        assertEquals(0, cursor.chunkX());
        assertEquals(0, cursor.chunkZ());
        for (int i = 0; i < 4; i++) assertFalse(cursor.advance(), "step " + i + " is mid-chunk");
        assertTrue(cursor.advance(), "the last position exhausts the chunk");
    }
    @Test void everyChunkInACycleReportsCompletionExactlyOnce() {
        var cursor = new BlockScanCursor();
        int horizontal = 20, vertical = 1;
        cursor.reset(8, 64, 8, horizontal, vertical);
        int chunks = ChunkScanOrder.around(8, 8, horizontal).size();
        int completions = 0;
        var completedChunks = new HashSet<String>();
        while (cursor.cycle() == 0) {
            String current = cursor.chunkX() + ":" + cursor.chunkZ();
            if (cursor.advance()) { completions++; completedChunks.add(current); }
        }
        assertEquals(chunks, completions, "each chunk is completed once per cycle");
        assertEquals(chunks, completedChunks.size(), "and no chunk is completed twice");
    }
    @Test void unloadedChunkSkipAndResetDiscardTheOldCursor() {
        var cursor = new BlockScanCursor();
        cursor.reset(0, 64, 0, 20, 5);
        cursor.skipChunk();
        assertFalse((cursor.x() >> 4) == 0 && (cursor.z() >> 4) == 0);
        cursor.reset(100, -30, 200, 0, 0);
        cursor.advance();
        assertEquals(100, cursor.x()); assertEquals(-30, cursor.y()); assertEquals(200, cursor.z());
        assertThrows(IllegalArgumentException.class, () -> cursor.reset(0, 0, 0, 65, 0));
    }
}
