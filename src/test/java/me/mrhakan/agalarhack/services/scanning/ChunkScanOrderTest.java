package me.mrhakan.agalarhack.services.scanning;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkScanOrderTest {
    @Test void boundedOrderStartsNearbyAndHandlesNegativeChunkBoundaries() {
        var chunks = ChunkScanOrder.around(-1, -17, 160);
        assertEquals(441, chunks.size());
        assertEquals(chunks.size(), new HashSet<>(chunks).size());
        assertEquals(new ChunkScanOrder.Chunk(-1, -2), chunks.getFirst());
        int distance = -1;
        for (var chunk : chunks) {
            int next = (chunk.x()+1)*(chunk.x()+1)+(chunk.z()+2)*(chunk.z()+2);
            assertTrue(next >= distance); distance = next;
        }
        assertThrows(IllegalArgumentException.class, () -> ChunkScanOrder.around(0, 0, 161));
    }
    @Test void storageKindsIncludeUndyedAndColoredShulkersWithoutClassifyingOtherBlocks() {
        assertEquals(StorageKind.SHULKER, StorageKind.of("minecraft:shulker_box"));
        assertEquals(StorageKind.SHULKER, StorageKind.of("minecraft:red_shulker_box"));
        assertEquals(StorageKind.ENDER_CHEST, StorageKind.of("minecraft:ender_chest"));
        assertEquals(StorageKind.UTILITY, StorageKind.of("minecraft:crafter"));
        assertEquals(StorageKind.OTHER, StorageKind.of("minecraft:stone"));
        assertEquals(StorageKind.OTHER, StorageKind.of(null));
    }
}
