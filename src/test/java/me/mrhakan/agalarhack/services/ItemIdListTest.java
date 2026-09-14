package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemIdListTest {
    @Test void bareNamesGetTheDefaultNamespace() {
        assertEquals("minecraft:rotten_flesh", ItemIdList.canonical("rotten_flesh"));
        assertEquals("minecraft:stone", ItemIdList.canonical("minecraft:stone"));
    }

    @Test void entriesAreTrimmedAndLowerCased() {
        assertEquals("minecraft:dirt", ItemIdList.canonical("  DIRT  "));
        assertEquals("mod:thing", ItemIdList.canonical("Mod:Thing"));
    }

    @Test void unusableEntriesAreDroppedRatherThanGuessedAt() {
        assertNull(ItemIdList.canonical(null));
        assertNull(ItemIdList.canonical("   "));
        assertNull(ItemIdList.canonical("bad id with spaces"));
        assertNull(ItemIdList.canonical("minecraft:"));
        assertNull(ItemIdList.canonical(":stone"));
        assertNull(ItemIdList.canonical("mine!craft:stone"));
        assertNull(ItemIdList.canonical("a".repeat(200)));
    }

    @Test void slashesAreAllowedInThePathOnly() {
        assertEquals("minecraft:block/stone", ItemIdList.canonical("block/stone"));
        assertNull(ItemIdList.canonical("na/mespace:stone"));
    }

    @Test void listsSplitOnCommasNewlinesAndSemicolons() {
        var ids = ItemIdList.parse("dirt, stone\ngravel;sand");
        assertEquals(java.util.List.of("minecraft:dirt", "minecraft:stone", "minecraft:gravel", "minecraft:sand"),
                java.util.List.copyOf(ids));
    }

    @Test void duplicatesCollapseAndOrderIsStable() {
        var ids = ItemIdList.parse("stone, minecraft:stone, STONE, dirt");
        assertEquals(java.util.List.of("minecraft:stone", "minecraft:dirt"), java.util.List.copyOf(ids));
    }

    @Test void blankAndNullInputProduceAnEmptyList() {
        assertTrue(ItemIdList.parse(null).isEmpty());
        assertTrue(ItemIdList.parse("   ").isEmpty());
        assertTrue(ItemIdList.parse(",,,").isEmpty());
    }

    @Test void theListIsBounded() {
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < ItemIdList.MAX_ENTRIES * 2; i++) raw.append("item_").append(i).append(',');
        assertEquals(ItemIdList.MAX_ENTRIES, ItemIdList.parse(raw.toString()).size());
    }

    @Test void garbageEntriesDoNotDiscardTheValidOnesAroundThem() {
        var ids = ItemIdList.parse("dirt, !!!, stone");
        assertEquals(java.util.List.of("minecraft:dirt", "minecraft:stone"), java.util.List.copyOf(ids));
    }
}
