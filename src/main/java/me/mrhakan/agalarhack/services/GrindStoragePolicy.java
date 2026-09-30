package me.mrhakan.agalarhack.services;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Three single chests: building materials, minerals, and other surplus. Keep usable supplies carried. */
public final class GrindStoragePolicy {
    private GrindStoragePolicy() { }
    public static int category(String item) {
        if (item.equals("log") || item.equals("planks") || item.equals("cobblestone") || item.equals("netherrack")) return 0;
        if (item.contains("ingot") || item.contains("scrap") || item.equals("diamond") || item.equals("coal")
                || item.startsWith("raw_") || item.equals("lapis_lazuli") || item.equals("ancient_debris")) return 1;
        return 2;
    }
    public static int reserve(String item) {
        if (item.endsWith("_pickaxe") || item.endsWith("_axe") || item.endsWith("_shovel") || item.endsWith("_sword")
                || item.endsWith("_helmet") || item.endsWith("_chestplate") || item.endsWith("_leggings") || item.endsWith("_boots")
                || item.equals("shield") || item.contains("bucket") || item.contains("template")
                || item.equals("flint_and_steel")) return Integer.MAX_VALUE;
        return switch (item) {
            case "log", "planks", "cobblestone", "torch" -> 32;
            case "coal", "iron_ingot", "diamond", "lapis_lazuli" -> 16;
            case "bread", "cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton" -> 24;
            case "crafting_table", "furnace", "chest" -> 1;
            default -> 8;
        };
    }
    /** Retain the current goal's recipe chain during an inventory-pressure trip. */
    public static Map<String, Integer> reservesFor(String item, int count) {
        Map<String, Integer> result = new LinkedHashMap<>();
        retain(item, count, result, new HashSet<>());
        return Map.copyOf(result);
    }
    /** Completed milestones may have consumed their stock; they must not reserve it again. */
    public static Map<String, Integer> remainingReserves(java.util.List<SurvivalProgression.Goal> taskGoals,
            int completed, java.util.function.Predicate<SurvivalProgression.Goal> satisfied) {
        Map<String, Integer> result = new LinkedHashMap<>();
        var remaining = taskGoals.subList(Math.min(completed, taskGoals.size()), taskGoals.size());
        for (var goal : new java.util.LinkedHashSet<>(remaining)) {
            if (goal.kind() != SurvivalProgression.Kind.ITEM || satisfied.test(goal)) continue;
            reservesFor(goal.item(), goal.count()).forEach((item, count) -> result.merge(item, count, Integer::sum));
        }
        return result;
    }
    private static void retain(String item, int count, Map<String, Integer> result, Set<String> visiting) {
        result.merge(item, count, Integer::sum);
        var recipe = GrindBook.recipes().get(item);
        if (recipe == null || !visiting.add(item)) return;
        int batches = (count + recipe.yield() - 1) / recipe.yield();
        recipe.ingredients().forEach((ingredient, amount) -> retain(ingredient, amount * batches, result, visiting));
        visiting.remove(item);
    }
}
