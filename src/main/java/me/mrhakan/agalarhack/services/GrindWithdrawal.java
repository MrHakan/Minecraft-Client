package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Which stored campaign supplies a goal should take back before it gathers anything.
 *
 * <p>The goal's recipe chain is walked top-down, the way {@link CraftingPlan} walks it. At each item
 * what is carried is spent first, then what is stored, and only the remainder is crafted from its
 * ingredients or left to be gathered. So a stored iron ingot is taken in preference to smelting
 * carried raw iron, and nothing is taken that carried stock already covers.
 *
 * <p>Kept free of Minecraft types: the caller describes a chest as {@link Stored} entries, already
 * filtered to what AutoGrind may take (see {@link GrindStorage#withdrawable}).
 */
final class GrindWithdrawal {
    private GrindWithdrawal() { }

    /**
     * Stacks of one kind in a chest.
     *
     * @param generic the name the recipe book uses ({@code planks} for any planks)
     * @param exact   the item's own id path ({@code birch_planks}); recipes that ask for a specific
     *                item match this instead
     */
    record Stored(String generic, String exact, int count) { }

    /**
     * @param carried what the player holds, keyed like {@link GrindItems#planningInventory}; never modified
     * @return what to take, by the name the recipe asks for, in the order the walk first needed it;
     *         empty when nothing stored helps
     */
    static Map<String, Integer> plan(String target, int wanted, Map<String, Integer> carried,
            List<Stored> stored, Map<String, CraftingPlan.Recipe> book) {
        if (target == null || wanted < 1 || stored == null || stored.isEmpty()) return Map.of();
        Walk walk = new Walk(new HashMap<>(carried == null ? Map.of() : carried), stored);
        walk.require(target, wanted, book == null ? Map.of() : book, new ArrayList<>());
        return Map.copyOf(walk.take);
    }

    private static final class Walk {
        private final Map<String, Integer> carried;
        private final List<Stored> stored;
        private final int[] left;
        private final Map<String, Integer> take = new LinkedHashMap<>();

        Walk(Map<String, Integer> carried, List<Stored> stored) {
            this.carried = carried;
            this.stored = stored;
            this.left = stored.stream().mapToInt(entry -> Math.max(0, entry.count())).toArray();
        }

        void require(String item, int count, Map<String, CraftingPlan.Recipe> book, List<String> chain) {
            int held = carried.getOrDefault(item, 0);
            int spend = Math.min(held, count);
            carried.put(item, held - spend);
            int missing = count - spend;
            if (missing <= 0) return;

            int taken = takeStored(item, missing);
            if (taken > 0) take.merge(item, taken, Integer::sum);
            missing -= taken;
            if (missing <= 0) return;

            CraftingPlan.Recipe recipe = book.get(item);
            // A cycle or an absurd depth is CraftingPlan's to report; here it only stops the walk.
            if (recipe == null || chain.contains(item) || chain.size() >= CraftingPlan.MAX_DEPTH) return;
            int batches = Math.ceilDiv(missing, recipe.yield());
            chain.add(item);
            for (String ingredient : new TreeSet<>(recipe.ingredients().keySet())) {
                require(ingredient, recipe.ingredients().get(ingredient) * batches, book, chain);
            }
            chain.remove(chain.size() - 1);
            // Crafting overshoots by whole batches; the spare output covers later needs.
            carried.merge(item, batches * recipe.yield() - missing, Integer::sum);
        }

        private int takeStored(String item, int wanted) {
            int taken = 0;
            for (int index = 0; index < left.length && taken < wanted; index++) {
                Stored entry = stored.get(index);
                if (left[index] == 0 || !(item.equals(entry.generic()) || item.equals(entry.exact()))) continue;
                int amount = Math.min(left[index], wanted - taken);
                left[index] -= amount;
                taken += amount;
            }
            return taken;
        }
    }
}
