package me.mrhakan.agalarhack.services;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrindExecutionPlanTest {
    private static int index(List<GrindExecutionPlan.Step> steps, GrindExecutionPlan.Action action,
            String item) {
        for (int i = 0; i < steps.size(); i++) {
            GrindExecutionPlan.Step step = steps.get(i);
            if (step.action() == action && step.item().equals(item)) return i;
        }
        return -1;
    }

    @Test void woodenPickaxePlanBuildsAndPlacesItsTableBeforeTheThreeByThreeRecipe() {
        List<GrindExecutionPlan.Step> steps = GrindExecutionPlan.plan(
                GrindBook.WOODEN_PICKAXE, 1, Map.of(), false, false);

        assertTrue(index(steps, GrindExecutionPlan.Action.GATHER, GrindBook.LOG) >= 0);
        assertTrue(index(steps, GrindExecutionPlan.Action.CRAFT, GrindBook.CRAFTING_TABLE) >= 0);
        assertTrue(index(steps, GrindExecutionPlan.Action.PLACE_TABLE, GrindBook.CRAFTING_TABLE)
                < index(steps, GrindExecutionPlan.Action.CRAFT, GrindBook.WOODEN_PICKAXE));
        assertTrue(index(steps, GrindExecutionPlan.Action.OPEN_TABLE, GrindBook.CRAFTING_TABLE)
                < index(steps, GrindExecutionPlan.Action.CRAFT, GrindBook.WOODEN_PICKAXE));
    }

    @Test void stoneAndIronGatheringHaveTheirRequiredPickaxeBeforeMining() {
        List<GrindExecutionPlan.Step> stone = GrindExecutionPlan.plan(
                GrindBook.STONE_PICKAXE, 1, Map.of(), false, false);
        List<GrindExecutionPlan.Step> iron = GrindExecutionPlan.plan(
                GrindBook.RAW_IRON, 1, Map.of(), false, false);

        assertTrue(index(stone, GrindExecutionPlan.Action.CRAFT, GrindBook.WOODEN_PICKAXE)
                < index(stone, GrindExecutionPlan.Action.GATHER, GrindBook.COBBLESTONE));
        assertTrue(index(stone, GrindExecutionPlan.Action.GATHER, GrindBook.COBBLESTONE)
                < index(stone, GrindExecutionPlan.Action.CRAFT, GrindBook.STONE_PICKAXE));
        assertTrue(index(iron, GrindExecutionPlan.Action.CRAFT, GrindBook.STONE_PICKAXE)
                < index(iron, GrindExecutionPlan.Action.GATHER, GrindBook.RAW_IRON));
    }

    @Test void anExistingStonePickaxeIsEnoughToGatherRawIron() {
        List<GrindExecutionPlan.Step> steps = GrindExecutionPlan.plan(GrindBook.RAW_IRON, 1,
                Map.of(GrindBook.STONE_PICKAXE, 1), false, false);

        assertEquals(1, steps.size());
        assertEquals(GrindExecutionPlan.Action.GATHER, steps.getFirst().action());
        assertEquals(GrindBook.RAW_IRON, steps.getFirst().item());
    }

    @Test void heldLogsAndTableAreUsedForWoodenPickaxeWithoutGatheringMoreLogs() {
        List<GrindExecutionPlan.Step> steps = GrindExecutionPlan.plan(GrindBook.WOODEN_PICKAXE, 1,
                Map.of(GrindBook.LOG, 2, GrindBook.CRAFTING_TABLE, 1), false, false);

        assertTrue(steps.stream().noneMatch(step -> step.action() == GrindExecutionPlan.Action.GATHER
                && step.item().equals(GrindBook.LOG)), steps.toString());
        assertTrue(steps.stream().noneMatch(step -> step.action() == GrindExecutionPlan.Action.CRAFT
                && step.item().equals(GrindBook.CRAFTING_TABLE)), steps.toString());
        assertTrue(index(steps, GrindExecutionPlan.Action.PLACE_TABLE, GrindBook.CRAFTING_TABLE) >= 0);
        assertTrue(index(steps, GrindExecutionPlan.Action.CRAFT, GrindBook.WOODEN_PICKAXE) >= 0);
    }

    @Test void ironIngotGoalBuildsFurnaceAndUsesSmeltingAfterResourcesAreReady() {
        List<GrindExecutionPlan.Step> steps = GrindExecutionPlan.plan(
                GrindBook.IRON_INGOT, 1, Map.of(), false, false);

        assertTrue(index(steps, GrindExecutionPlan.Action.GATHER, GrindBook.COBBLESTONE) >= 0);
        assertTrue(index(steps, GrindExecutionPlan.Action.CRAFT, GrindBook.FURNACE) >= 0);
        assertTrue(index(steps, GrindExecutionPlan.Action.PLACE_FURNACE, GrindBook.FURNACE)
                < index(steps, GrindExecutionPlan.Action.SMELT, GrindBook.IRON_INGOT));
        assertTrue(index(steps, GrindExecutionPlan.Action.OPEN_FURNACE, GrindBook.FURNACE)
                < index(steps, GrindExecutionPlan.Action.SMELT, GrindBook.IRON_INGOT));
        assertTrue(index(steps, GrindExecutionPlan.Action.GATHER, GrindBook.RAW_IRON)
                < index(steps, GrindExecutionPlan.Action.SMELT, GrindBook.IRON_INGOT));
        assertTrue(index(steps, GrindExecutionPlan.Action.GATHER, GrindBook.COAL)
                < index(steps, GrindExecutionPlan.Action.SMELT, GrindBook.IRON_INGOT));
    }

    @Test void existingToolsAndStationsAvoidRebuildingPrerequisites() {
        List<GrindExecutionPlan.Step> steps = GrindExecutionPlan.plan(GrindBook.COBBLESTONE, 3,
                Map.of(GrindBook.IRON_PICKAXE, 1), true, true);

        assertEquals(1, steps.size());
        assertEquals(GrindExecutionPlan.Action.GATHER, steps.getFirst().action());
        assertEquals(GrindBook.COBBLESTONE, steps.getFirst().item());
    }

    @Test void aFurnaceAlreadyInInventoryDoesNotForceUnneededToolOrTableCrafts() {
        List<GrindExecutionPlan.Step> steps = GrindExecutionPlan.plan(GrindBook.IRON_INGOT, 1,
                Map.of(GrindBook.FURNACE, 1, GrindBook.RAW_IRON, 8, GrindBook.COAL, 1), false, false);

        assertEquals(GrindExecutionPlan.Action.PLACE_FURNACE,
                steps.getFirst().action());
        assertTrue(index(steps, GrindExecutionPlan.Action.CRAFT, GrindBook.WOODEN_PICKAXE) < 0);
        assertTrue(index(steps, GrindExecutionPlan.Action.CRAFT, GrindBook.CRAFTING_TABLE) < 0);
        assertTrue(index(steps, GrindExecutionPlan.Action.PLACE_FURNACE, GrindBook.FURNACE)
                < index(steps, GrindExecutionPlan.Action.SMELT, GrindBook.IRON_INGOT));
    }

    @Test void craftingLayoutsMatchEveryCraftingRecipeAndStayInsideTheirGrid() {
        for (var entry : GrindBook.recipes().entrySet()) {
            if (GrindBook.IRON_INGOT.equals(entry.getKey())) continue;
            boolean table = entry.getValue().needsTable();
            Map<String, List<Integer>> layout = GrindRecipeLayouts.inputs(entry.getKey(), table);
            assertFalse(layout.isEmpty(), entry.getKey());
            assertEquals(entry.getValue().ingredients().keySet(), layout.keySet(), entry.getKey());
            for (var ingredient : entry.getValue().ingredients().entrySet()) {
                assertEquals(ingredient.getValue(), GrindRecipeLayouts.count(layout, ingredient.getKey()), entry.getKey());
            }
            int gridSize = table ? 9 : 4;
            assertTrue(layout.values().stream().flatMap(List::stream).allMatch(slot -> slot >= 0 && slot < gridSize), entry.getKey());
            long unique = layout.values().stream().flatMap(List::stream).distinct().count();
            int total = layout.values().stream().mapToInt(List::size).sum();
            assertEquals(total, unique, entry.getKey());
        }
    }

    @Test void everyCurrentGrindBookGoalProducesExecutableStepsAndSkipsHeldGoals() {
        for (String target : List.of(GrindBook.LOG, GrindBook.PLANKS, GrindBook.STICK,
                GrindBook.CRAFTING_TABLE, GrindBook.COBBLESTONE, GrindBook.COAL,
                GrindBook.RAW_IRON, GrindBook.IRON_INGOT, GrindBook.FURNACE,
                GrindBook.WOODEN_PICKAXE, GrindBook.STONE_PICKAXE, GrindBook.IRON_PICKAXE)) {
            List<GrindExecutionPlan.Step> steps = assertDoesNotThrow(() ->
                    GrindExecutionPlan.plan(target, 1, Map.of(), false, false), target);
            assertFalse(steps.isEmpty(), target);
            assertTrue(GrindExecutionPlan.plan(target, 1, Map.of(target, 1), true, true).isEmpty(), target);
        }
    }
}
