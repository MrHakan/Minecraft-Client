package me.mrhakan.agalarhack.services;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The mapping from real item ids onto the names the planner counts, and the book it plans against. */
class GrindBookTest {

    @Test void everyWoodVariantCountsAsTheSameLog() {
        for (String id : List.of("minecraft:oak_log", "minecraft:birch_log", "spruce_log",
                "minecraft:stripped_acacia_log", "minecraft:crimson_stem", "minecraft:warped_hyphae",
                "minecraft:oak_wood")) {
            assertEquals(GrindBook.LOG, GrindBook.generic(id), id + " did not count as a log");
        }
    }

    @Test void everyPlankVariantCountsAsPlanks() {
        for (String id : List.of("minecraft:oak_planks", "jungle_planks", "minecraft:bamboo_planks")) {
            assertEquals(GrindBook.PLANKS, GrindBook.generic(id), id + " did not count as planks");
        }
    }

    @Test void charcoalBurnsLikeCoal() {
        assertEquals(GrindBook.COAL, GrindBook.generic("minecraft:charcoal"));
        assertEquals(GrindBook.COAL, GrindBook.generic("minecraft:coal"));
    }

    @Test void deepslateCobbleStillCountsAsCobblestone() {
        assertEquals(GrindBook.COBBLESTONE, GrindBook.generic("minecraft:cobbled_deepslate"));
    }

    /** An id the book has never heard of keeps its own name rather than being silently reclassified. */
    @Test void anUnknownItemKeepsItsOwnName() {
        assertEquals("diamond", GrindBook.generic("minecraft:diamond"));
        assertEquals("", GrindBook.generic(null));
    }

    @Test void aStonePickaxeResolvesToWoodAndStone() {
        var steps = CraftingPlan.plan(GrindBook.STONE_PICKAXE, 1, Map.of(), GrindBook.recipes());
        assertEquals(List.of(GrindBook.COBBLESTONE, GrindBook.LOG), CraftingPlan.gathered(steps));
        assertEquals(GrindBook.STONE_PICKAXE, steps.get(steps.size() - 1).item());
        assertTrue(steps.get(steps.size() - 1).needsTable(), "a pickaxe needs a crafting table");
    }

    /** The whole early game in one goal, which is the shape a real run asks for. */
    @Test void anIronPickaxeNeedsWoodStoneOreAndFuel() {
        var steps = CraftingPlan.plan(GrindBook.IRON_PICKAXE, 1, Map.of(), GrindBook.recipes());
        // Order follows the deterministic name ordering: the ingot's own ingredients (coal, raw
        // iron) resolve before the sticks, and the sticks pull in the wood.
        assertEquals(List.of(GrindBook.COAL, GrindBook.RAW_IRON, GrindBook.LOG),
                CraftingPlan.gathered(steps));
        assertEquals(GrindBook.IRON_PICKAXE, steps.get(steps.size() - 1).item());
    }

    @Test void woodAlreadyCarriedIsNotGatheredAgain() {
        var steps = CraftingPlan.plan(GrindBook.CRAFTING_TABLE, 1, Map.of(GrindBook.PLANKS, 4),
                GrindBook.recipes());
        assertEquals(List.of(), CraftingPlan.gathered(steps), "it already had the planks");
        assertEquals(1, steps.size());
    }

    @Test void theBookRecognisesWhatItCanBeAskedFor() {
        assertTrue(GrindBook.knows(GrindBook.IRON_PICKAXE));
        assertTrue(GrindBook.knows(GrindBook.LOG), "a raw material is a legitimate goal on its own");
        assertFalse(GrindBook.knows("elytra"));
        assertFalse(GrindBook.knows(null));
    }
}
