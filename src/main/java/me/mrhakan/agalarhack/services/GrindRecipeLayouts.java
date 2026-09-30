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
        } else if ("oak_planks".equals(output)) {
            result.put("oak_log", List.of(table ? 4 : 0));
        } else if (output.matches("(stone|iron|diamond)_(pickaxe|axe|shovel|sword)")) {
            String material = output.substring(0, output.indexOf('_'));
            String head = material.equals("stone") ? GrindBook.COBBLESTONE : material.equals("iron") ? GrindBook.IRON_INGOT : "diamond";
            String tool = output.substring(output.indexOf('_') + 1);
            result.put(head, switch (tool) {
                case "pickaxe" -> List.of(0, 1, 2); case "axe" -> List.of(0, 1, 3);
                case "shovel" -> List.of(1); default -> List.of(1, 4);
            });
            result.put(GrindBook.STICK, tool.equals("sword") ? List.of(7) : List.of(4, 7));
        } else if (output.matches("(iron|diamond)_(helmet|chestplate|leggings|boots)")) {
            String head = output.startsWith("iron_") ? GrindBook.IRON_INGOT : "diamond";
            result.put(head, switch (output.substring(output.indexOf('_') + 1)) {
                case "helmet" -> List.of(0, 1, 2, 3, 5); case "chestplate" -> List.of(0, 2, 3, 4, 5, 6, 7, 8);
                case "leggings" -> List.of(0, 1, 2, 3, 5, 6, 8); default -> List.of(3, 5, 6, 8);
            });
        } else {
            switch (output) {
                case "shield" -> { result.put(GrindBook.PLANKS, List.of(0, 2, 3, 4, 5, 7)); result.put(GrindBook.IRON_INGOT, List.of(1)); }
                case "bucket" -> result.put(GrindBook.IRON_INGOT, List.of(3, 5, 7));
                case "torch" -> { result.put(GrindBook.COAL, List.of(table ? 1 : 0)); result.put(GrindBook.STICK, List.of(table ? 4 : 2)); }
                case "chest" -> result.put(GrindBook.PLANKS, List.of(0, 1, 2, 3, 5, 6, 7, 8));
                case "oak_door" -> result.put("oak_planks", List.of(0, 1, 3, 4, 6, 7));
                case "bread" -> result.put("wheat", List.of(3, 4, 5));
                case "paper" -> result.put("sugar_cane", List.of(3, 4, 5));
                case "book" -> { result.put("paper", table ? List.of(0, 1, 3) : List.of(0, 1, 2)); result.put("leather", List.of(table ? 4 : 3)); }
                case "bookshelf" -> { result.put(GrindBook.PLANKS, List.of(0, 1, 2, 6, 7, 8)); result.put("book", List.of(3, 4, 5)); }
                case "enchanting_table" -> { result.put("book", List.of(1)); result.put("diamond", List.of(3, 5)); result.put("obsidian", List.of(4, 6, 7, 8)); }
                case "smithing_table" -> { result.put(GrindBook.IRON_INGOT, List.of(0, 1)); result.put(GrindBook.PLANKS, List.of(3, 4, 6, 7)); }
                case "flint_and_steel" -> { result.put(GrindBook.IRON_INGOT, List.of(0)); result.put("flint", List.of(table ? 4 : 3)); }
                case "netherite_ingot" -> { result.put("netherite_scrap", List.of(0, 1, 2, 3)); result.put("gold_ingot", List.of(4, 5, 6, 7)); }
                default -> { return Map.of(); }
            }
        }
        if (!table && result.values().stream().flatMap(List::stream).anyMatch(slot -> slot >= 4)) return Map.of();
        return Collections.unmodifiableMap(result);
    }

    /** Ingredient count needed to fill one vanilla recipe. */
    public static int count(Map<String, List<Integer>> inputs, String ingredient) {
        return inputs.getOrDefault(ingredient, List.of()).size();
    }
}
