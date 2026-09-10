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
