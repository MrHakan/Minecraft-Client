package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.ItemStack;

/** Smelts or cooks one item in an open furnace and collects the result. Moved out of {@link GrindExecutor} in 2.0.04; it reaches the executor's shared state through {@code g}. */
final class GrindSmeltTask implements TaskRunner.Task {
    private final GrindExecutor g;

    private final String output;
    private final String inputItem;
    private final int goal;
    private final int rawIron;
    private final int coal;
    private boolean initialized;
    private boolean outputClickQueued;
    private int initialIngotCount;
    private int initialCoalCount;
    private int outputCountBeforeClick;
    private int resultWaitTicks;
    private String movementReason;
    private String failureReason;

    GrindSmeltTask(GrindExecutor g, String output, int goal, int rawIron, int coal) {
        this.g = g;
        this.output = output; this.inputItem = GrindBook.smeltInput(output); this.goal = goal; this.rawIron = rawIron; this.coal = coal;
    }
    @Override public String name() { return "smelt " + output + " (" + g.count(output) + "/" + goal + ")"; }
    @Override public boolean satisfied() {
        // Keep the station task alive until its final output/recovery click is acknowledged by
        // the shared transfer controller, for the same reason as GrindCraftTask above.
        return g.count(output) >= goal && !g.inventory.transfers().owns(GrindExecutor.OWNER);
    }
    @Override public int budgetTicks() { return Math.min(140_000, Math.max(TaskRunner.DEFAULT_BUDGET_TICKS, rawIron * 220 + 4_000)); }

    @Override public boolean tick() {
        movementReason = null;
        if (failureReason != null) return false;
        if (satisfied()) return true;
        if (!(g.client.player.containerMenu instanceof FurnaceMenu menu)) {
            if (g.closeOwnedStationMenu()) return true;
            movementReason = "Open the nearby furnace and run .grind resume.";
            return true;
        }
        if (g.inventory.transfers().busy()) {
            if (g.inventory.transfers().recoveryBlocked()) movementReason = g.inventoryRecoveryReason();
            return true;
        }
        int menuId = menu.containerId;
        if (!initialized) {
            if (!menu.getSlot(0).getItem().isEmpty() || !menu.getSlot(1).getItem().isEmpty() || !menu.getSlot(2).getItem().isEmpty()) {
                failureReason = "the nearby furnace already contains items; AutoGrind left them untouched";
                return false;
            }
            initialIngotCount = g.count(output);
            initialCoalCount = g.count(GrindBook.COAL);
            initialized = true;
        }
        if (outputClickQueued) {
            if (satisfied()) return true;
            if (g.count(output) <= outputCountBeforeClick) {
                resultWaitTicks++;
                if (resultWaitTicks > 10 && !menu.getSlot(2).getItem().isEmpty()) {
                    outputClickQueued = false;
                    resultWaitTicks = 0;
                    return true;
                }
                if (resultWaitTicks > 40) {
                    failureReason = "the furnace output did not reach the inventory";
                    return false;
                }
                return true;
            }
            outputClickQueued = false;
        }
        ItemStack result = menu.getSlot(2).getItem();
        if (!result.isEmpty()) {
            if (!output.equals(
                    GrindBook.generic(BuiltInRegistries.ITEM.getKey(result.getItem()).toString()))) {
                failureReason = "the furnace contains an unrelated output; AutoGrind left it untouched";
                return false;
            }
            outputCountBeforeClick = g.count(output);
            if (!g.inventory.transfers().beginClicks(GrindExecutor.OWNER, GrindExecutor.PRIORITY, menuId,
                    new ContainerTransferController.Click[]{new ContainerTransferController.Click(2, 0)}, 0)) return true;
            outputClickQueued = true;
            resultWaitTicks = 0;
            return true;
        }

        ItemStack input = menu.getSlot(0).getItem();
        if (!input.isEmpty() && !inputItem.equals(
                GrindBook.generic(BuiltInRegistries.ITEM.getKey(input.getItem()).toString()))) {
            failureReason = "the furnace input is not raw iron; AutoGrind left it untouched";
            return false;
        }
        ItemStack fuel = menu.getSlot(1).getItem();
        if (!fuel.isEmpty() && !GrindBook.COAL.equals(
                GrindBook.generic(BuiltInRegistries.ITEM.getKey(fuel.getItem()).toString()))) {
            failureReason = "the furnace fuel slot contains an unrelated item; AutoGrind left it untouched";
            return false;
        }

        // Raw iron already cooking, output waiting in the result slot, and ingots already
        // collected all count toward the same task. This lets a resumed task continue after
        // the click queue was safely cancelled when a station screen closed.
        int produced = Math.max(0, g.count(output) - initialIngotCount);
        int availableOutput = 0;
        ItemStack pendingOutput = menu.getSlot(2).getItem();
        if (!pendingOutput.isEmpty()) availableOutput = pendingOutput.getCount();
        int inputInFurnace = input.isEmpty() ? 0 : input.getCount();
        int remainingRaw = Math.max(0, rawIron - produced - availableOutput - inputInFurnace);
        int fuelMoved = Math.max(0, initialCoalCount - g.count(GrindBook.COAL));
        int remainingFuel = Math.max(0, coal - fuelMoved);
        int rawSlotRoom = Math.max(0, 64 - inputInFurnace);
        int loadRaw = Math.min(remainingRaw, rawSlotRoom);
        if (loadRaw > 0 || remainingFuel > 0) {
            if (g.count(inputItem) < loadRaw || g.count(GrindBook.COAL) < remainingFuel) {
                failureReason = "missing raw iron or coal in the inventory for the remaining smelting batch";
                return false;
            }
            List<ContainerTransferController.Click> clicks = new ArrayList<>();
            if (loadRaw > 0 && !appendFurnaceInput(clicks, inputItem, loadRaw, 0)
                    || remainingFuel > 0 && !appendFurnaceInput(clicks, GrindBook.COAL, remainingFuel, 1)) {
                failureReason = "could not prepare the remaining raw iron or coal for smelting";
                return false;
            }
            if (!g.inventory.transfers().beginClicks(GrindExecutor.OWNER, GrindExecutor.PRIORITY, menuId,
                    clicks.toArray(ContainerTransferController.Click[]::new), 0)) return true;
            return true;
        }
        return true;
    }

    private boolean appendFurnaceInput(List<ContainerTransferController.Click> clicks,
            String ingredient, int amount, int furnaceSlot) {
        return g.appendIngredientClicks(clicks, ingredient, java.util.Collections.nCopies(amount, furnaceSlot),
                InventoryTransfers.PlayerMenuLayout.FURNACE, true);
    }

    @Override public String blockedReason() { return movementReason; }
    @Override public String failureReason() { return failureReason; }
    @Override public void cancel() { g.inventory.transfers().release(GrindExecutor.OWNER); }
}
