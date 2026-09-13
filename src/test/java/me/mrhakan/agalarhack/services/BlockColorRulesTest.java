package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlockColorRulesTest {

    @Test
    void parsesPairsAndCanonicalisesTheId() {
        Map<String, Integer> colors = BlockColorRules.parse("diamond_ore=00ffff, minecraft:spawner=#FF5555");
        assertEquals(Map.of("minecraft:diamond_ore", 0x00FFFF, "minecraft:spawner", 0xFF5555), colors);
    }

    @Test
    void dropsMalformedEntriesRatherThanGuessing() {
        Map<String, Integer> colors = BlockColorRules.parse(
                "diamond_ore=00ffff, no_equals_sign, spawner=xyzxyz, beacon=f00, =00ff00, gold_ore=");
        assertEquals(Map.of("minecraft:diamond_ore", 0x00FFFF), colors,
                "three-digit shorthand, non-hex, empty id and empty colour must all be dropped");
    }

    @Test
    void lastEntryForAnIdWinsAndTakesTheLastPosition() {
        Map<String, Integer> colors = BlockColorRules.parse("diamond_ore=111111, spawner=222222, diamond_ore=333333");
        assertEquals(0x333333, colors.get("minecraft:diamond_ore"));
        assertEquals(List.of("minecraft:spawner", "minecraft:diamond_ore"), List.copyOf(colors.keySet()));
    }

    @Test
    void boundsTheEntryCount() {
        StringBuilder raw = new StringBuilder();
        for (int index = 0; index < BlockColorRules.MAX_ENTRIES * 3; index++) {
            raw.append("block_").append(index).append("=0000ff,");
        }
        assertEquals(BlockColorRules.MAX_ENTRIES, BlockColorRules.parse(raw.toString()).size());
    }

    @Test
    void parseColorAcceptsSixDigitsWithOrWithoutHash() {
        assertEquals(0xAABBCC, BlockColorRules.parseColor("aabbcc"));
        assertEquals(0xAABBCC, BlockColorRules.parseColor("#AABBCC"));
        assertEquals(0x000000, BlockColorRules.parseColor("000000"));
        assertEquals(0xFFFFFF, BlockColorRules.parseColor("  ffffff  "));
        assertEquals(BlockColorRules.NO_COLOR, BlockColorRules.parseColor("fffffff"));
        assertEquals(BlockColorRules.NO_COLOR, BlockColorRules.parseColor(null));
    }

    @Test
    void deepslateVariantsShareTheirOreColour() {
        assertEquals(BlockColorRules.categoryColor("minecraft:diamond_ore"),
                BlockColorRules.categoryColor("minecraft:deepslate_diamond_ore"));
        assertEquals(BlockColorRules.categoryColor("minecraft:lapis_ore"),
                BlockColorRules.categoryColor("minecraft:deepslate_lapis_ore"));
    }

    @Test
    void categoryColourIgnoresNamespace() {
        assertEquals(BlockColorRules.categoryColor("minecraft:spawner"),
                BlockColorRules.categoryColor("someothermod:spawner"));
    }

    @Test
    void categoryColourHasNoOpinionAboutUnknownBlocks() {
        assertEquals(BlockColorRules.NO_COLOR, BlockColorRules.categoryColor("minecraft:dirt"));
        assertEquals(BlockColorRules.NO_COLOR, BlockColorRules.categoryColor(null));
    }

    @Test
    void distinctBlocksGetDistinctBuiltInColours() {
        List<String> ids = List.of("minecraft:diamond_ore", "minecraft:emerald_ore", "minecraft:gold_ore",
                "minecraft:iron_ore", "minecraft:copper_ore", "minecraft:coal_ore", "minecraft:redstone_ore",
                "minecraft:lapis_ore", "minecraft:spawner", "minecraft:beacon", "minecraft:ancient_debris");
        long distinct = ids.stream().map(BlockColorRules::categoryColor).distinct().count();
        assertEquals(ids.size(), distinct, "a shared colour would defeat the point of per-block colouring");
        assertTrue(ids.stream().allMatch(id -> BlockColorRules.categoryColor(id) != BlockColorRules.NO_COLOR));
    }

    @Test
    void resolveAppliesOverrideThenCategoryThenFallback() {
        Map<String, Integer> overrides = BlockColorRules.parse("diamond_ore=010203");
        assertEquals(0x010203, BlockColorRules.resolve(overrides, "minecraft:diamond_ore", true, 0xFFFFFF));
        assertEquals(BlockColorRules.categoryColor("minecraft:spawner"),
                BlockColorRules.resolve(overrides, "minecraft:spawner", true, 0xFFFFFF));
        assertEquals(0xFFFFFF, BlockColorRules.resolve(overrides, "minecraft:dirt", true, 0xFFFFFF));
    }

    @Test
    void disablingCategoriesStillHonoursExplicitOverrides() {
        Map<String, Integer> overrides = BlockColorRules.parse("spawner=010203");
        assertEquals(0x010203, BlockColorRules.resolve(overrides, "minecraft:spawner", false, 0xFFFFFF));
        assertEquals(0xFFFFFF, BlockColorRules.resolve(overrides, "minecraft:diamond_ore", false, 0xFFFFFF),
                "with categories off the module colour must win, not the built-in one");
    }

    @Test
    void resolvedColoursStayInsideTwentyFourBits() {
        Map<String, Integer> overrides = BlockColorRules.parse("diamond_ore=ffffff");
        for (String id : List.of("minecraft:diamond_ore", "minecraft:spawner", "minecraft:dirt")) {
            int rgb = BlockColorRules.resolve(overrides, id, true, 0x123456);
            assertTrue(rgb >= 0 && rgb <= 0xFFFFFF, id + " produced " + Integer.toHexString(rgb));
        }
    }
}
