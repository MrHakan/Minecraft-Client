package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GrindWithdrawalTest {
    private static Map<String, Integer> plan(String target, int wanted, Map<String, Integer> carried,
            GrindWithdrawal.Stored... stored) {
        return GrindWithdrawal.plan(target, wanted, carried, List.of(stored), GrindBook.recipes());
    }

    private static GrindWithdrawal.Stored stored(String item, int count) {
        return new GrindWithdrawal.Stored(item, item, count);
    }

    @Test
    void takesWhatTheRecipeNeedsAndNoMore() {
        assertEquals(Map.of("iron_ingot", 3, "stick", 2),
                plan("iron_pickaxe", 1, Map.of("crafting_table", 1), stored("iron_ingot", 10), stored("stick", 16)));
    }

    @Test
    void carriedStockIsSpentBeforeStoredStock() {
        assertEquals(Map.of("iron_ingot", 1),
                plan("iron_pickaxe", 1, Map.of("iron_ingot", 2, "stick", 2), stored("iron_ingot", 10), stored("stick", 16)));
        assertEquals(Map.of(), plan("iron_pickaxe", 1, Map.of("iron_ingot", 3, "stick", 2), stored("iron_ingot", 10)),
                "a goal the player can already make takes nothing");
    }

    @Test
    void aStoredIngotIsPreferredToSmeltingCarriedRawIron() {
        assertEquals(Map.of("iron_ingot", 3),
                plan("iron_pickaxe", 1, Map.of("raw_iron", 8, "coal", 1, "stick", 2), stored("iron_ingot", 3)));
    }

    @Test
    void whatIsNotStoredFallsThroughToItsIngredients() {
        // One ingot short: the recipe's whole smelting batch is eight raw iron and one coal.
        assertEquals(Map.of("iron_ingot", 2, "raw_iron", 5, "coal", 1),
                plan("iron_pickaxe", 1, Map.of("stick", 2), stored("iron_ingot", 2), stored("raw_iron", 5), stored("coal", 4)));
    }

    @Test
    void yieldsAreRespected() {
        // Five planks are two logs, not five.
        assertEquals(Map.of("log", 2), plan("planks", 5, Map.of(), stored("log", 20)));
    }

    @Test
    void genericRecipesMatchAnyVariantAndExactOnesOnlyTheirOwn() {
        GrindWithdrawal.Stored birch = new GrindWithdrawal.Stored("planks", "birch_planks", 8);
        assertEquals(Map.of("planks", 4), plan("crafting_table", 1, Map.of(), birch));
        assertEquals(Map.of(), plan("oak_planks", 4, Map.of(), birch), "birch planks are not oak planks");
    }

    @Test
    void neverTakesMoreThanIsStored() {
        assertEquals(Map.of("cobblestone", 5), plan("cobblestone", 20, Map.of(), stored("cobblestone", 5)));
    }

    @Test
    void nothingUsefulStoredMeansNothingToTake() {
        assertEquals(Map.of(), plan("iron_pickaxe", 1, Map.of(), stored("dirt", 64)));
        assertEquals(Map.of(), plan("iron_pickaxe", 1, Map.of()));
    }

    @Test
    void oneStoredStackIsSharedAcrossTheChainNotCountedTwice() {
        // A wooden pickaxe needs three planks and two sticks, and the sticks need two planks more.
        // Four stored planks cover the head and half the sticks; the rest is gathered, not taken twice.
        assertEquals(Map.of("planks", 4), plan("wooden_pickaxe", 1, Map.of(), stored("planks", 4)));
    }
}
