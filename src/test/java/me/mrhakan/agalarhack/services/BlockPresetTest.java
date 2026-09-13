package me.mrhakan.agalarhack.services;

import me.mrhakan.agalarhack.module.render.BlockESP;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockPresetTest {
    @Test void everyPresetParsesIntoCanonicalIds() {
        for (String preset : new String[] { "ores", "ancient_debris", "spawners", "portals",
                "beacons", "containers", "redstone", "valuables" }) {
            var ids = ItemIdList.parse(BlockESP.presetBlocks(preset));
            assertFalse(ids.isEmpty(), preset + " should not be empty");
            for (String id : ids) {
                assertTrue(id.startsWith("minecraft:"), preset + " produced " + id);
                assertEquals(id, ItemIdList.canonical(id), preset + " produced a non-canonical id");
            }
        }
    }

    @Test void unknownAndMissingPresetsAreEmptyRatherThanGuessed() {
        assertEquals("", BlockESP.presetBlocks(null));
        assertEquals("", BlockESP.presetBlocks("none"));
        assertEquals("", BlockESP.presetBlocks("not-a-preset"));
    }

    @Test void presetNamesAreCaseInsensitive() {
        assertEquals(BlockESP.presetBlocks("ores"), BlockESP.presetBlocks("ORES"));
    }

    @Test void presetsStayInsideTheParserLimit() {
        for (String preset : new String[] { "ores", "containers", "valuables", "redstone" }) {
            assertTrue(ItemIdList.parse(BlockESP.presetBlocks(preset)).size() <= ItemIdList.MAX_ENTRIES);
        }
    }

    @Test void valuablesAndOresOverlapWithoutDuplicatingAfterMerging() {
        var merged = new java.util.LinkedHashSet<>(ItemIdList.parse(BlockESP.presetBlocks("ores")));
        int before = merged.size();
        merged.addAll(ItemIdList.parse(BlockESP.presetBlocks("valuables")));
        assertTrue(merged.size() > before, "valuables adds ids ores does not have");
        assertEquals(merged.size(), new java.util.LinkedHashSet<>(merged).size());
    }
}
