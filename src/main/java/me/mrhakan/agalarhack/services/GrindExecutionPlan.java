package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Adds the world operations needed to carry out a {@link CraftingPlan} without changing it. */
public final class GrindExecutionPlan {
    private GrindExecutionPlan() { }

    public enum Action { GATHER, CRAFT, PLACE_TABLE, OPEN_TABLE, PLACE_FURNACE, OPEN_FURNACE, SMELT }

    /** Counts are item totals, matching {@link CraftingPlan.Step#count()}. */
    public record Step(Action action, String item, int count, boolean needsTable) { }

    /**
     * Expands the live plan with tool and station preparation. The planner is re-run against the
     * simulated inventory after each operation, so supplies made for a prerequisite satisfy the
     * requested goal rather than causing duplicate gathering or crafting.
     */
    public static List<Step> plan(String target, int wanted, Map<String, Integer> have,
            boolean nearbyCraftingTable, boolean nearbyFurnace) {
        Builder builder = new Builder(new LinkedHashMap<>(have == null ? Map.of() : have),
                nearbyCraftingTable, nearbyFurnace);
        builder.compileGoal(target, wanted);
        return List.copyOf(builder.steps);
    }

    private static final class Builder {
        private static final int MAX_EXPANSIONS = 2048;
        private final Map<String, Integer> inventory;
        private final List<Step> steps = new ArrayList<>();
        private final Set<Integer> ensuringTools = new HashSet<>();
        private final Set<String> compilingGoals = new HashSet<>();
        private boolean tablePlaced;
        private boolean furnacePlaced;
        private int expansions;

        private Builder(Map<String, Integer> inventory, boolean nearbyCraftingTable, boolean nearbyFurnace) {
            this.inventory = inventory;
            tablePlaced = nearbyCraftingTable;
            furnacePlaced = nearbyFurnace;
        }

        private void compileGoal(String target, int wanted) {
            if (!compilingGoals.add(target)) throw new IllegalArgumentException("Executor dependency cycle at " + target);
            try {
                while (true) {
                    if (++expansions > MAX_EXPANSIONS) throw new IllegalArgumentException("Executor plan exceeded its bounded step count");
                    List<CraftingPlan.Step> plan = CraftingPlan.plan(target, wanted, inventory, GrindBook.recipes());
                    if (plan.isEmpty()) return;
                    appendNext(plan.get(0));
                }
            } finally {
                compilingGoals.remove(target);
            }
        }

        private void appendNext(CraftingPlan.Step work) {
            if (work.kind() == CraftingPlan.Kind.GATHER) {
                int tier = toolTierFor(work.item());
                if (tier > 0 && !hasPickaxe(tier)) {
                    ensurePickaxe(tier);
                    return;
                }
                steps.add(new Step(Action.GATHER, work.item(), work.count(), false));
                inventory.merge(work.item(), work.count(), Integer::sum);
                return;
            }

            if (GrindBook.IRON_INGOT.equals(work.item())) {
                if (!furnacePlaced) {
                    ensureFurnace();
                    return;
                }
                steps.add(new Step(Action.OPEN_FURNACE, GrindBook.FURNACE, 1, false));
                steps.add(new Step(Action.SMELT, work.item(), work.count(), false));
                simulateCraft(work.item(), work.count());
                return;
            }

            if (work.needsTable()) {
                if (!tablePlaced) {
                    ensureTable();
                    return;
                }
                steps.add(new Step(Action.OPEN_TABLE, GrindBook.CRAFTING_TABLE, 1, true));
            }
            steps.add(new Step(Action.CRAFT, work.item(), work.count(), work.needsTable()));
            simulateCraft(work.item(), work.count());
        }

        private void ensurePickaxe(int tier) {
            if (hasPickaxe(tier) || !ensuringTools.add(tier)) return;
            try {
                if (tier >= 2) ensurePickaxe(1);
                compileGoal(tier >= 2 ? GrindBook.STONE_PICKAXE : GrindBook.WOODEN_PICKAXE, 1);
            } finally {
                ensuringTools.remove(tier);
            }
        }

        private void ensureTable() {
            if (tablePlaced) return;
            if (inventory.getOrDefault(GrindBook.CRAFTING_TABLE, 0) < 1) {
                compileGoal(GrindBook.CRAFTING_TABLE, 1);
            }
            steps.add(new Step(Action.PLACE_TABLE, GrindBook.CRAFTING_TABLE, 1, false));
            inventory.merge(GrindBook.CRAFTING_TABLE, -1, Integer::sum);
            tablePlaced = true;
        }

        private void ensureFurnace() {
            if (furnacePlaced) return;
            // Let the ordinary recipe expansion decide whether tools or a table are needed. A
            // furnace already in the player's inventory can be placed without making a pickaxe;
            // when one must be crafted, its cobblestone gather and 3x3 recipe add those prerequisites.
            compileGoal(GrindBook.FURNACE, 1);
            steps.add(new Step(Action.PLACE_FURNACE, GrindBook.FURNACE, 1, false));
            inventory.merge(GrindBook.FURNACE, -1, Integer::sum);
            furnacePlaced = true;
        }

        private boolean hasPickaxe(int minimumTier) {
            return inventory.entrySet().stream().anyMatch(entry -> entry.getValue() > 0
                    && pickaxeTier(entry.getKey()) >= minimumTier);
        }

        private void simulateCraft(String item, int outputCount) {
            CraftingPlan.Recipe recipe = GrindBook.recipes().get(item);
            if (recipe == null) return;
            int crafts = outputCount / recipe.yield();
            recipe.ingredients().forEach((ingredient, count) ->
                    inventory.merge(ingredient, -count * crafts, Integer::sum));
            inventory.merge(item, outputCount, Integer::sum);
        }
    }

    private static int toolTierFor(String resource) {
        return switch (resource) {
            case GrindBook.COBBLESTONE, GrindBook.COAL -> 1;
            case GrindBook.RAW_IRON -> 2;
            default -> 0;
        };
    }

    /** Vanilla pickaxe tiers, including better tools a player may already own. */
    public static int pickaxeTier(String genericItem) {
        if (genericItem == null) return 0;
        return switch (genericItem) {
            case GrindBook.WOODEN_PICKAXE, "golden_pickaxe" -> 1;
            case GrindBook.STONE_PICKAXE, "copper_pickaxe" -> 2;
            case GrindBook.IRON_PICKAXE, "diamond_pickaxe" -> 3;
            case "netherite_pickaxe" -> 4;
            default -> 0;
        };
    }
}
