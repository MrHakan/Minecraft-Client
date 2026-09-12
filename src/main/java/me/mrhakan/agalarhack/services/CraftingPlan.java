package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns "I want a stone pickaxe" into the ordered steps that actually produce one.
 *
 * <p>This is the part of automated grinding that is arithmetic rather than game logic, and getting
 * it wrong is expensive in a way that is hard to see: a plan that forgets a craft's yield gathers
 * four times the wood it needs, and a plan that spends the same planks twice sends the player mining
 * for something they are holding. Both look like the bot "working" right up until it stalls.
 *
 * <p>So it is kept free of Minecraft entirely. The recipe book and the current inventory are passed
 * in as plain maps, which means every rule below is tested directly:
 * <ul>
 *   <li><strong>Yields.</strong> One log gives four planks, so needing five planks is two logs, not
 *       five, and the spare three planks stay available to later steps.</li>
 *   <li><strong>Shared ingredients.</strong> A working copy of the inventory is spent as the plan is
 *       built, so planks used for sticks are not also counted towards the crafting table.</li>
 *   <li><strong>What is already held.</strong> Anything in the inventory reduces what is gathered,
 *       which is what lets a plan be re-run after it was interrupted without starting over.</li>
 *   <li><strong>Unknown items are gathered.</strong> Anything with no recipe - logs, cobblestone,
 *       iron ore - becomes a gather step rather than an error, because that is the honest answer.</li>
 *   <li><strong>The same goal gives the same plan.</strong> Ingredients are walked in name order, so
 *       two runs are comparable and a failure is reproducible.</li>
 * </ul>
 *
 * <p>The steps come back in the order they must happen: everything an item depends on appears before
 * the item itself.
 */
public final class CraftingPlan {
    private CraftingPlan() { }

    /** How deep a chain may go before this refuses; far beyond any real crafting tree. */
    public static final int MAX_DEPTH = 16;

    /**
     * @param output      what the recipe makes
     * @param yield       how many it makes per craft, which is what stops a plan over-gathering
     * @param ingredients item to count, per single craft
     * @param needsTable  true for a 3x3 recipe, which the executor must have a crafting table for
     */
    public record Recipe(String output, int yield, Map<String, Integer> ingredients, boolean needsTable) {
        public Recipe {
            if (output == null || output.isBlank()) throw new IllegalArgumentException("Recipe needs an output");
            if (yield < 1) throw new IllegalArgumentException("Recipe yield must be at least one");
            ingredients = Map.copyOf(ingredients == null ? Map.of() : ingredients);
            for (var entry : ingredients.entrySet()) {
                if (entry.getValue() == null || entry.getValue() < 1) {
                    throw new IllegalArgumentException("Ingredient count must be at least one: " + entry.getKey());
                }
            }
        }
    }

    public enum Kind {
        /** No recipe: it has to be mined, chopped, killed or picked up. */
        GATHER,
        CRAFT
    }

    /**
     * @param count for {@link Kind#GATHER} the number of items to obtain; for {@link Kind#CRAFT} the
     *              number of times to perform the craft, not the number of items it produces
     */
    public record Step(Kind kind, String item, int count, boolean needsTable) { }

    /**
     * Builds the plan.
     *
     * @param have a snapshot of what is already held; never modified
     * @return the steps in the order they must be done, empty when the goal is already met
     * @throws IllegalArgumentException on a recipe cycle or a chain deeper than {@link #MAX_DEPTH}
     */
    public static List<Step> plan(String target, int wanted, Map<String, Integer> have,
            Map<String, Recipe> book) {
        if (target == null || target.isBlank()) throw new IllegalArgumentException("Plan needs a target");
        if (wanted < 1) return List.of();
        Map<String, Integer> stock = new LinkedHashMap<>(have == null ? Map.of() : have);
        Map<String, Recipe> recipes = book == null ? Map.of() : book;
        List<Step> steps = new ArrayList<>();
        resolve(target, wanted, stock, recipes, steps, new ArrayList<>());
        return List.copyOf(steps);
    }

    private static void resolve(String item, int wanted, Map<String, Integer> stock,
            Map<String, Recipe> book, List<Step> steps, List<String> chain) {
        int held = stock.getOrDefault(item, 0);
        int short_ = wanted - held;
        if (short_ <= 0) {
            // Already covered by what is held; spend it so nothing else counts the same items.
            stock.put(item, held - wanted);
            return;
        }
        // Everything held is spent towards this, and the rest has to be produced.
        stock.put(item, 0);

        Recipe recipe = book.get(item);
        if (recipe == null) {
            steps.add(new Step(Kind.GATHER, item, short_, false));
            return;
        }
        if (chain.contains(item)) {
            throw new IllegalArgumentException("Recipe cycle: " + String.join(" -> ", chain) + " -> " + item);
        }
        if (chain.size() >= MAX_DEPTH) {
            throw new IllegalArgumentException("Recipe chain deeper than " + MAX_DEPTH + " at " + item);
        }

        int batches = Math.ceilDiv(short_, recipe.yield());
        chain.add(item);
        // Ingredients are resolved in name order, not map order, so the same goal always produces
        // the same plan. `Map.of` randomises its iteration per JVM run, so without this the steps
        // come out in a different order from one launch to the next: a player comparing two runs
        // sees noise, and a test asserting the order passes or fails by luck. This was found
        // exactly that way.
        for (String ingredient : new java.util.TreeSet<>(recipe.ingredients().keySet())) {
            resolve(ingredient, recipe.ingredients().get(ingredient) * batches, stock, book, steps, chain);
        }
        chain.remove(chain.size() - 1);

        steps.add(new Step(Kind.CRAFT, item, batches, recipe.needsTable()));
        // A craft that overshoots leaves the remainder for later steps, which is why five planks
        // costs two logs rather than five and the spare three are not gathered again.
        int produced = batches * recipe.yield();
        stock.put(item, produced - short_);
    }

    /** Every distinct item a plan has to gather, in plan order, for a progress display. */
    public static List<String> gathered(List<Step> steps) {
        List<String> items = new ArrayList<>();
        for (Step step : steps) {
            if (step.kind() == Kind.GATHER && !items.contains(step.item())) items.add(step.item());
        }
        return List.copyOf(items);
    }
}
