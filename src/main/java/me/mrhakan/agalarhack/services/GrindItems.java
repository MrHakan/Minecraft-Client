package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * What AutoGrind carries: counting by generic name, preparing the hotbar and the right tool, and
 * building crafting-grid clicks from the carried stacks.
 * Moved out of {@link GrindExecutor} in 2.0.06; it reaches the executor's shared state through
 * {@code g}.
 */
final class GrindItems {
    private final GrindExecutor g;
    private static final int SOURCE_OFFHAND = -2;
    private static final EquipmentSlot[] NON_STORAGE_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.OFFHAND
    };

    GrindItems(GrindExecutor g) {
        this.g = g;
    }

    Map<String, Integer> planningInventory(String target) {
        Map<String, Integer> have = carried();
        // A Silk Touch pickaxe cannot provide ore drops for prerequisite chains.
        for (int slot = 0; slot < InventoryTransfers.INVENTORY_SIZE; slot++) excludeSilkTool(have, target, g.inventory.stackAt(slot));
        excludeSilkTool(have, target, g.inventory.equipped(EquipmentSlot.OFFHAND));
        if (target.equals("oak_door") || target.equals("oak_planks") || target.equals("oak_log")) {
            // Exact wood and its generic alias describe the same stacks, never two supplies.
            have.merge(GrindBook.PLANKS, -have.getOrDefault("oak_planks", 0), Integer::sum);
            have.merge(GrindBook.LOG, -have.getOrDefault("oak_log", 0), Integer::sum);
        }
        return have;
    }

    private static void excludeSilkTool(Map<String, Integer> have, String target, ItemStack stack) {
        String item = SurvivalTasks.id(stack);
        if (!item.equals(target) && item.endsWith("_pickaxe") && usable(stack) && hasSilkTouch(stack))
            have.merge(item, -stack.getCount(), Integer::sum);
    }

    private int findHotbarItem(String generic) {
        for (int slot = 0; slot < InventoryTransfers.HOTBAR_SIZE; slot++) {
            if (countStack(g.inventory.stackAt(slot), generic) > 0) return slot;
        }
        return -1;
    }

    /** Returns a player inventory index, SOURCE_OFFHAND, or -1 when the item is not carried. */
    private int findCarriedSlot(String generic) {
        for (int slot = 0; slot < InventoryTransfers.INVENTORY_SIZE; slot++) {
            if (countStack(g.inventory.stackAt(slot), generic) > 0) return slot;
        }
        if (countStack(g.inventory.equipped(EquipmentSlot.OFFHAND), generic) > 0) return SOURCE_OFFHAND;
        return -1;
    }

    /** Moves a carried item into the hotbar through the shared owner/click channel when needed. */
    int prepareHotbarItem(String generic) {
        int hotbar = findHotbarItem(generic);
        if (hotbar >= 0) return hotbar;
        int source = findCarriedSlot(generic);
        if (source < 0 && source != SOURCE_OFFHAND) return -1;
        if (g.inventory.transfers().busy()) return GrindExecutor.HOTBAR_PENDING;
        int destination = -1;
        for (int slot = 0; slot < InventoryTransfers.HOTBAR_SIZE; slot++) {
            if (g.inventory.stackAt(slot).isEmpty()) { destination = slot; break; }
        }
        if (destination < 0) destination = Math.max(0, g.inventory.selectedSlot());
        int sourceMenuSlot = source == SOURCE_OFFHAND
                ? InventoryTransfers.MENU_OFFHAND : InventoryTransfers.menuSlot(source);
        int[] clicks = {sourceMenuSlot, InventoryTransfers.menuSlot(destination), sourceMenuSlot};
        g.inventory.transfers().begin(GrindExecutor.OWNER, GrindExecutor.PRIORITY, clicks, 0);
        return GrindExecutor.HOTBAR_PENDING;
    }

    int correctToolSlot(net.minecraft.world.level.block.state.BlockState state, String resource) {
        int bestSlot = -1;
        double bestSpeed = -1;
        for (int slot = 0; slot < InventoryTransfers.HOTBAR_SIZE; slot++) {
            ItemStack stack = g.inventory.stackAt(slot);
            if (!validTool(stack, state, resource)) continue;
            double speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) { bestSpeed = speed; bestSlot = slot; }
        }
        if (bestSlot >= 0) return bestSlot;

        int bestStorage = -1;
        bestSpeed = -1;
        for (int slot = InventoryTransfers.HOTBAR_SIZE; slot < InventoryTransfers.INVENTORY_SIZE; slot++) {
            ItemStack stack = g.inventory.stackAt(slot);
            if (!validTool(stack, state, resource)) continue;
            double speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) { bestSpeed = speed; bestStorage = slot; }
        }
        if (bestStorage >= 0) {
            if (!g.inventory.transfers().busy()) {
                int destination = findEmptyHotbarSlot();
                if (destination < 0) destination = Math.max(0, g.inventory.selectedSlot());
                g.inventory.transfers().begin(GrindExecutor.OWNER, GrindExecutor.PRIORITY,
                        InventoryTransfers.equipPlan(bestStorage, InventoryTransfers.menuSlot(destination)), 0);
            }
            return GrindExecutor.HOTBAR_PENDING;
        }

        ItemStack offhand = g.inventory.equipped(EquipmentSlot.OFFHAND);
        if (validTool(offhand, state, resource)) {
            if (!g.inventory.transfers().busy()) {
                int destination = findEmptyHotbarSlot();
                if (destination < 0) destination = Math.max(0, g.inventory.selectedSlot());
                g.inventory.transfers().begin(GrindExecutor.OWNER, GrindExecutor.PRIORITY, new int[]{InventoryTransfers.MENU_OFFHAND,
                        InventoryTransfers.menuSlot(destination), InventoryTransfers.MENU_OFFHAND}, 0);
            }
            return GrindExecutor.HOTBAR_PENDING;
        }
        return -1;
    }

    int findEmptyHotbarSlot() {
        for (int slot = 0; slot < InventoryTransfers.HOTBAR_SIZE; slot++) {
            if (g.inventory.stackAt(slot).isEmpty()) return slot;
        }
        return -1;
    }

    static boolean validTool(ItemStack stack, net.minecraft.world.level.block.state.BlockState state,
            String resource) {
        if (stack.isEmpty() || (stack.isDamageableItem()
                && stack.getMaxDamage() - stack.getDamageValue() <= 1) || !stack.isCorrectToolForDrops(state)) {
            return false;
        }
        // Silk Touch changes the drop of stone and the two ores this executor understands. Do not
        // claim a resource task can finish with a tool that would leave its item count unchanged.
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        boolean silkChangesDrop = (GrindBook.COBBLESTONE.equals(resource)
                && (path.equals("stone") || path.equals("deepslate")))
                || GrindBook.COAL.equals(resource) && (path.equals("coal_ore") || path.endsWith("_coal_ore"))
                || GrindBook.RAW_IRON.equals(resource) && (path.equals("iron_ore") || path.endsWith("_iron_ore"))
                || resource.equals("diamond") || resource.equals("lapis_lazuli") || resource.equals("raw_gold");
        return !silkChangesDrop || !hasSilkTouch(stack);
    }

    private static boolean hasSilkTouch(ItemStack stack) {
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) return false;
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (holder.is(Enchantments.SILK_TOUCH) && enchantments.getLevel(holder) > 0) return true;
        }
        return false;
    }

    String inventoryRecoveryReason() {
        return "Make the inventory menu available and free a slot so AutoGrind can return the carried stack, then run .grind resume.";
    }

    /** Counts through the shared inventory snapshot boundary rather than a second inventory path. */
    Map<String, Integer> carried() {
        Map<String, Integer> have = new LinkedHashMap<>();
        if (g.client == null || g.client.player == null) return have;
        for (int slot = 0; slot < InventoryTransfers.INVENTORY_SIZE; slot++) addStack(have, g.inventory.stackAt(slot));
        for (EquipmentSlot slot : NON_STORAGE_SLOTS) addStack(have, g.inventory.equipped(slot));
        return have;
    }

    int count(String generic) {
        if (g.client == null || g.client.player == null) return 0;
        int total = 0;
        for (int slot = 0; slot < InventoryTransfers.INVENTORY_SIZE; slot++) {
            total += countStack(g.inventory.stackAt(slot), generic);
        }
        for (EquipmentSlot slot : NON_STORAGE_SLOTS) total += countStack(g.inventory.equipped(slot), generic);
        return total;
    }

    private static void addStack(Map<String, Integer> have, ItemStack stack) {
        if (!usable(stack)) return;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String generic = GrindBook.generic(id);
        have.merge(generic, stack.getCount(), Integer::sum);
        String exact = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (!exact.equals(generic)) have.merge(exact, stack.getCount(), Integer::sum);
    }

    static int countStack(ItemStack stack, String generic) {
        return usable(stack)
                && (GrindBook.generic(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).equals(generic)
                || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(generic))
                ? stack.getCount() : 0;
    }

    static boolean usable(ItemStack stack) {
        return !stack.isEmpty() && (!stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > 1);
    }

    List<ContainerTransferController.Click> craftingInputClicks(String output, boolean table, int menuId) {
        CraftingPlan.Recipe recipe = GrindBook.recipes().get(output);
        Map<String, List<Integer>> layout = GrindRecipeLayouts.inputs(output, table);
        int gridSize = table ? 9 : 4;
        Map<Integer, String> expected = new LinkedHashMap<>();
        for (var ingredient : layout.entrySet()) {
            for (int cell : ingredient.getValue()) expected.put(cell + 1, ingredient.getKey());
        }
        Map<String, List<Integer>> emptyTargets = new LinkedHashMap<>();
        for (int slot = 1; slot <= gridSize; slot++) {
            ItemStack placed = g.client.player.containerMenu.getSlot(slot).getItem();
            String wanted = expected.get(slot);
            if (placed.isEmpty()) {
                if (wanted != null) emptyTargets.computeIfAbsent(wanted, ignored -> new ArrayList<>()).add(slot);
            } else if (wanted == null || countStack(placed, wanted) == 0) {
                return null;
            }
        }
        InventoryTransfers.PlayerMenuLayout inventoryLayout = table
                ? InventoryTransfers.PlayerMenuLayout.CRAFTING_TABLE : InventoryTransfers.PlayerMenuLayout.INVENTORY;
        List<ContainerTransferController.Click> clicks = new ArrayList<>();
        for (String ingredient : new TreeSet<>(recipe.ingredients().keySet())) {
            List<Integer> targetSlots = emptyTargets.getOrDefault(ingredient, List.of());
            if (!appendIngredientClicks(clicks, ingredient, targetSlots, inventoryLayout, table)) return null;
        }
        return clicks;
    }

    boolean appendIngredientClicks(List<ContainerTransferController.Click> clicks, String ingredient,
            List<Integer> targetSlots, InventoryTransfers.PlayerMenuLayout layout, boolean tableMenu) {
        int remaining = targetSlots.size();
        for (int index = 0; index < InventoryTransfers.INVENTORY_SIZE && remaining > 0; index++) {
            ItemStack source = g.inventory.stackAt(index);
            int available = countStack(source, ingredient);
            if (available <= 0) continue;
            int amount = Math.min(available, remaining);
            int menuSlot = InventoryTransfers.menuSlot(index, layout);
            clicks.add(new ContainerTransferController.Click(menuSlot, 0));
            for (int i = 0; i < amount; i++) {
                int gridSlot = targetSlots.get(targetSlots.size() - remaining + i);
                clicks.add(new ContainerTransferController.Click(gridSlot, 1));
            }
            if (amount < available) clicks.add(new ContainerTransferController.Click(menuSlot, 0));
            remaining -= amount;
        }
        if (!tableMenu && remaining > 0) {
            ItemStack offhand = g.inventory.equipped(EquipmentSlot.OFFHAND);
            int available = countStack(offhand, ingredient);
            int amount = Math.min(available, remaining);
            if (amount > 0) {
                clicks.add(new ContainerTransferController.Click(InventoryTransfers.MENU_OFFHAND, 0));
                for (int i = 0; i < amount; i++) {
                    int gridSlot = targetSlots.get(targetSlots.size() - remaining + i);
                    clicks.add(new ContainerTransferController.Click(gridSlot, 1));
                }
                if (amount < available) clicks.add(new ContainerTransferController.Click(InventoryTransfers.MENU_OFFHAND, 0));
                remaining -= amount;
            }
        }
        return remaining == 0;
    }

    String resultItem(int menuId, String expected) {
        if (g.client.player == null || g.client.player.containerMenu == null
                || (menuId >= 0 && g.client.player.containerMenu.containerId != menuId)) return "";
        ItemStack output = g.client.player.containerMenu.getSlot(0).getItem();
        return countStack(output, expected) > 0 ? expected : "";
    }
}
