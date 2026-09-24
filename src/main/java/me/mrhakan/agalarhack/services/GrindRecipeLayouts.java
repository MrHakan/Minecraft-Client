package me.mrhakan.agalarhack.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/** Exact vanilla crafting-grid positions for the small deterministic GrindBook recipe set. */
public final class GrindRecipeLayouts {
    private GrindRecipeLayouts() { }

    /** Ingredient name to row-major grid slot indices (not menu slot ids). */
    public static Map<String, List<Integer>> inputs(String output, boolean table) {
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        if (GrindBook.PLANKS.equals(output)) {
            result.put(GrindBook.LOG, List.of(table ? 4 : 0));
        } else if (GrindBook.STICK.equals(output)) {
            result.put(GrindBook.PLANKS, table ? List.of(1, 4) : List.of(0, 2));
        } else if (GrindBook.CRAFTING_TABLE.equals(output)) {
            result.put(GrindBook.PLANKS, table ? List.of(0, 1, 3, 4) : List.of(0, 1, 2, 3));
        } else if (GrindBook.WOODEN_PICKAXE.equals(output)) {
            result.put(GrindBook.PLANKS, List.of(0, 1, 2));
            result.put(GrindBook.STICK, List.of(4, 7));
        } else if (GrindBook.STONE_PICKAXE.equals(output)) {
            result.put(GrindBook.COBBLESTONE, List.of(0, 1, 2));
            result.put(GrindBook.STICK, List.of(4, 7));
        } else if (GrindBook.IRON_PICKAXE.equals(output)) {
            result.put(GrindBook.IRON_INGOT, List.of(0, 1, 2));
            result.put(GrindBook.STICK, List.of(4, 7));
        } else if (GrindBook.FURNACE.equals(output)) {
            result.put(GrindBook.COBBLESTONE, List.of(0, 1, 2, 3, 5, 6, 7, 8));
        } else {
            return Map.of();
        }
        if (!table && result.values().stream().flatMap(List::stream).anyMatch(slot -> slot >= 4)) return Map.of();
        return Collections.unmodifiableMap(result);
    }

    /** Ingredient count needed to fill one vanilla recipe. */
    public static int count(Map<String, List<Integer>> inputs, String ingredient) {
        return inputs.getOrDefault(ingredient, List.of()).size();
    }
}
