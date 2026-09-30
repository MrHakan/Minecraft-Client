package me.mrhakan.agalarhack.services;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurvivalProgressionTest {
    @Test void everyItemMilestoneHasAnExecutablePrerequisiteChain() {
        for (SurvivalProgression.Tier tier : SurvivalProgression.Tier.values())
            for (var goal : SurvivalProgression.goals(tier)) if (goal.kind() == SurvivalProgression.Kind.ITEM) {
                assertTrue(GrindBook.knows(goal.item()), goal.item());
                assertDoesNotThrow(() -> GrindExecutionPlan.plan(goal.item(), goal.count(), Map.of(), false, false), goal.item());
            }
    }
    @Test void progressionStopsAtTheSelectedTierAndPlacesShelterBeforeDiamondAndNether() {
        List<SurvivalProgression.Goal> iron = SurvivalProgression.goals(SurvivalProgression.Tier.IRON);
        assertTrue(iron.stream().noneMatch(g -> g.item().startsWith("diamond") || g.item().startsWith("netherite")));
        List<SurvivalProgression.Goal> diamond = SurvivalProgression.goals(SurvivalProgression.Tier.DIAMOND);
        int house = find(diamond, "starter house"), pick = find(diamond, "diamond_pickaxe");
        assertTrue(house > 0 && house < pick);
        assertTrue(diamond.stream().noneMatch(g -> g.kind() == SurvivalProgression.Kind.NETHER));
        var max = SurvivalProgression.goals(SurvivalProgression.Tier.MAX);
        assertTrue(find(max, "diamond_pickaxe") < find(max, "Nether portal"));
        assertTrue(find(max, "enter Nether") < find(max, "ancient_debris"));
        assertTrue(find(max, "return home") < find(max, "netherite_helmet"));
    }
    @Test void diamondRequiresIronAndObsidianRequiresDiamondBeforeGathering() {
        for (String resource : List.of("diamond", "obsidian", "ancient_debris")) {
            var plan = GrindExecutionPlan.plan(resource, 1, Map.of(), false, false);
            String tool = resource.equals("diamond") ? "iron_pickaxe" : "diamond_pickaxe";
            int craft = -1, gather = -1;
            for (int i = 0; i < plan.size(); i++) {
                if (plan.get(i).item().equals(tool) && plan.get(i).action() == GrindExecutionPlan.Action.CRAFT) craft = i;
                if (plan.get(i).item().equals(resource) && plan.get(i).action() == GrindExecutionPlan.Action.GATHER) gather = i;
            }
            assertTrue(craft >= 0 && craft < gather, plan.toString());
        }
        assertEquals(3, GrindExecutionPlan.pickaxeTier("iron_pickaxe"));
        assertEquals(4, GrindExecutionPlan.pickaxeTier("diamond_pickaxe"));
    }
    @Test void netheriteUsesASmithingActionAfterItsIngredientsAndStation() {
        var plan = GrindExecutionPlan.plan("netherite_helmet", 1,
                Map.of("diamond_helmet", 1, "netherite_ingot", 1, "netherite_upgrade_smithing_template", 1), true, true);
        assertEquals(GrindExecutionPlan.Action.SMITH, plan.getLast().action());
        assertTrue(plan.stream().anyMatch(s -> s.item().equals("smithing_table")));
        assertTrue(plan.stream().noneMatch(s -> s.action() == GrindExecutionPlan.Action.CRAFT && s.item().equals("netherite_helmet")));
    }
    @Test void houseHasNoOverlappingPlacementsAndKeepsTheDoorAndChestLidsClear() {
        for (boolean expanded : List.of(false, true)) {
            var blueprint = SurvivalBlueprint.house(expanded);
            HashSet<String> occupied = new HashSet<>();
            for (var p : blueprint) assertTrue(occupied.add(p.x() + ":" + p.y() + ":" + p.z()), p.toString());
            var door = blueprint.stream().filter(p -> p.item().equals("oak_door")).findFirst().orElseThrow();
            assertFalse(occupied.contains(door.x() + ":2:" + door.z()));
            for (var p : blueprint) if (p.item().equals("chest")) assertFalse(occupied.contains(p.x() + ":2:" + p.z()));
            assertEquals(blueprint.size(), SurvivalBlueprint.materials(expanded).values().stream().mapToInt(Integer::intValue).sum());
            assertEquals(expanded ? 3 : 1, blueprint.stream().filter(p -> p.item().equals("chest")).count());
        }
    }
    @Test void portalHasContinuousSupportsAndEmptyInterior() {
        var portal = SurvivalBlueprint.portal();
        assertEquals(14, portal.size());
        HashSet<String> placed = new HashSet<>();
        for (var p : portal) {
            assertTrue(p.y() == 0 || placed.contains((p.x()-1) + ":" + p.y())
                    || placed.contains(p.x() + ":" + (p.y()-1)), p.toString());
            assertFalse((p.x() == 1 || p.x() == 2) && p.y() > 0 && p.y() < 4);
            placed.add(p.x() + ":" + p.y());
        }
    }
    @Test void storageKeepsEquipmentTemplatesFoodAndUsefulBuildingSupplies() {
        assertEquals(Integer.MAX_VALUE, GrindStoragePolicy.reserve("netherite_pickaxe"));
        assertEquals(Integer.MAX_VALUE, GrindStoragePolicy.reserve("netherite_upgrade_smithing_template"));
        assertEquals(24, GrindStoragePolicy.reserve("cooked_beef"));
        assertEquals(32, GrindStoragePolicy.reserve("cobblestone"));
        assertEquals(0, GrindStoragePolicy.category("planks"));
        assertEquals(1, GrindStoragePolicy.category("diamond"));
        assertEquals(2, GrindStoragePolicy.category("leather"));
    }
    @Test void extendedStationLayoutsMapEveryPlayerSlotExactlyOnce() {
        for (var layout : List.of(InventoryTransfers.PlayerMenuLayout.CHEST,
                InventoryTransfers.PlayerMenuLayout.SMITHING, InventoryTransfers.PlayerMenuLayout.ENCHANTING)) {
            HashSet<Integer> slots = new HashSet<>();
            int start = switch(layout) { case CHEST -> 27; case SMITHING -> 4; default -> 2; };
            for (int i = 0; i < 36; i++) {
                int slot = InventoryTransfers.menuSlot(i, layout);
                assertTrue(slot >= start && slot < start + 36);
                assertTrue(slots.add(slot));
            }
        }
    }
    private static int find(List<SurvivalProgression.Goal> goals, String item) {
        for (int i = 0; i < goals.size(); i++) if (goals.get(i).item().equals(item)) return i;
        return -1;
    }
}
