package me.mrhakan.agalarhack.services;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The arithmetic that decides how much a bot actually has to gather.
 *
 * <p>Written against a toy recipe book rather than Minecraft's, because every rule here is about
 * counting: yields, leftovers, ingredients shared between two steps, and what is already in the
 * bag. A real book only changes the numbers.
 */
class CraftingPlanTest {

    private static final Map<String, CraftingPlan.Recipe> BOOK = Map.of(
            "planks", new CraftingPlan.Recipe("planks", 4, Map.of("log", 1), false),
            "stick", new CraftingPlan.Recipe("stick", 4, Map.of("planks", 2), false),
            "crafting_table", new CraftingPlan.Recipe("crafting_table", 1, Map.of("planks", 4), false),
            "stone_pickaxe", new CraftingPlan.Recipe("stone_pickaxe", 1,
                    Map.of("cobblestone", 3, "stick", 2), true),
            // Six planks and a stick, yielding three: chosen because none of the numbers divide
            // evenly, which is where a plan that mishandles leftovers gathers wood twice.
            "sign", new CraftingPlan.Recipe("sign", 3, Map.of("planks", 6, "stick", 1), true));

    private static List<CraftingPlan.Step> plan(String target, int wanted, Map<String, Integer> have) {
        return CraftingPlan.plan(target, wanted, have, BOOK);
    }

    private static CraftingPlan.Step step(CraftingPlan.Kind kind, String item, int count) {
        return new CraftingPlan.Step(kind, item, count, false);
    }

    @Test void somethingAlreadyHeldNeedsNoPlan() {
        assertEquals(List.of(), plan("stone_pickaxe", 1, Map.of("stone_pickaxe", 1)));
    }

    @Test void anUnknownItemIsGatheredRatherThanRefused() {
        assertEquals(List.of(step(CraftingPlan.Kind.GATHER, "log", 3)), plan("log", 3, Map.of()));
    }

    @Test void whatIsHeldReducesWhatIsGathered() {
        assertEquals(List.of(step(CraftingPlan.Kind.GATHER, "log", 2)), plan("log", 5, Map.of("log", 3)));
    }

    /** The whole point of tracking yields: five planks is two logs, not five. */
    @Test void aYieldIsNotIgnored() {
        var steps = plan("planks", 5, Map.of());
        assertEquals(List.of(
                step(CraftingPlan.Kind.GATHER, "log", 2),
                step(CraftingPlan.Kind.CRAFT, "planks", 2)), steps);
    }

    @Test void anExactMultipleDoesNotOverGather() {
        var steps = plan("planks", 8, Map.of());
        assertEquals(step(CraftingPlan.Kind.GATHER, "log", 2), steps.get(0));
        assertEquals(step(CraftingPlan.Kind.CRAFT, "planks", 2), steps.get(1));
    }

    /** Leftovers from an earlier craft must be spent before anything more is gathered. */
    @Test void spareOutputFromACraftIsUsedByLaterSteps() {
        // Four sticks need two planks; one log yields four planks, so the other two must not be
        // gathered again for the crafting table.
        var steps = CraftingPlan.plan("crafting_table", 1, Map.of("stick", 0), BOOK);
        assertEquals(List.of(
                step(CraftingPlan.Kind.GATHER, "log", 1),
                step(CraftingPlan.Kind.CRAFT, "planks", 1),
                step(CraftingPlan.Kind.CRAFT, "crafting_table", 1)), steps);
    }

    /**
     * The sharp case: two logs make eight planks, six go into the sign, and the two left over must
     * cover the stick's planks. A plan that forgets them gathers a third log for wood it is holding.
     */
    @Test void leftoversFromAnUnevenCraftCoverTheNextStep() {
        var steps = plan("sign", 1, Map.of());
        assertEquals(1, steps.stream().filter(s -> s.item().equals("log")).count(),
                "the log was gathered more than once: " + steps);
        var log = steps.stream().filter(s -> s.item().equals("log")).findFirst().orElseThrow();
        assertEquals(2, log.count(), "two logs cover eight planks; the plan asked for " + log.count());
        assertEquals(List.of("log"), CraftingPlan.gathered(steps),
                "nothing but wood should have to be gathered for a sign");
    }

    @Test void ingredientsAreNotCountedTwiceAcrossSteps() {
        // A stone pickaxe needs two sticks and three cobblestone. Sticks come from planks, planks
        // from a log. Nothing here may assume the same planks serve twice.
        var steps = plan("stone_pickaxe", 1, Map.of());
        assertEquals(List.of(
                step(CraftingPlan.Kind.GATHER, "cobblestone", 3),
                step(CraftingPlan.Kind.GATHER, "log", 1),
                step(CraftingPlan.Kind.CRAFT, "planks", 1),
                step(CraftingPlan.Kind.CRAFT, "stick", 1),
                new CraftingPlan.Step(CraftingPlan.Kind.CRAFT, "stone_pickaxe", 1, true)), steps);
    }

    @Test void dependenciesAlwaysComeBeforeWhatNeedsThem() {
        var steps = plan("stone_pickaxe", 2, Map.of());
        int planks = -1, sticks = -1, pickaxe = -1;
        for (int i = 0; i < steps.size(); i++) {
            switch (steps.get(i).item()) {
                case "planks" -> planks = i;
                case "stick" -> sticks = i;
                case "stone_pickaxe" -> pickaxe = i;
                default -> { }
            }
        }
        assertTrue(planks < sticks, "planks must be crafted before the sticks that need them");
        assertTrue(sticks < pickaxe, "sticks must exist before the pickaxe that needs them");
    }

    @Test void aPartlyStockedInventoryOnlyFillsTheGap() {
        var steps = plan("stone_pickaxe", 1, Map.of("cobblestone", 3, "stick", 2));
        assertEquals(List.of(new CraftingPlan.Step(CraftingPlan.Kind.CRAFT, "stone_pickaxe", 1, true)), steps);
    }

    @Test void aThreeByThreeRecipeSaysItNeedsATable() {
        var steps = plan("stone_pickaxe", 1, Map.of("cobblestone", 3, "stick", 2));
        assertTrue(steps.get(0).needsTable(), "the executor has to know it needs a crafting table");
    }

    @Test void craftCountsAreBatchesNotItems() {
        var steps = plan("stick", 5, Map.of("planks", 10));
        var craft = steps.stream().filter(s -> s.kind() == CraftingPlan.Kind.CRAFT).findFirst().orElseThrow();
        assertEquals(2, craft.count(), "five sticks is two crafts of four, not five crafts");
    }

    @Test void wantingNoneIsNotAPlan() {
        assertEquals(List.of(), plan("stone_pickaxe", 0, Map.of()));
    }

    @Test void aRecipeCycleIsRefusedRatherThanLoopingForever() {
        Map<String, CraftingPlan.Recipe> cyclic = Map.of(
                "a", new CraftingPlan.Recipe("a", 1, Map.of("b", 1), false),
                "b", new CraftingPlan.Recipe("b", 1, Map.of("a", 1), false));
        var failure = assertThrows(IllegalArgumentException.class,
                () -> CraftingPlan.plan("a", 1, Map.of(), cyclic));
        assertTrue(failure.getMessage().contains("cycle"), failure.getMessage());
    }

    @Test void theCallersInventoryIsNeverModified() {
        var have = new java.util.LinkedHashMap<>(Map.of("log", 10));
        plan("planks", 4, have);
        assertEquals(10, have.get("log"), "planning spent the caller's real inventory");
    }

    @Test void gatheredNamesEveryRawItemOnce() {
        var steps = plan("stone_pickaxe", 2, Map.of());
        assertEquals(List.of("cobblestone", "log"), CraftingPlan.gathered(steps));
    }

    @Test void aRecipeWithoutAYieldIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new CraftingPlan.Recipe("x", 0, Map.of("y", 1), false));
    }
}
