package me.mrhakan.agalarhack.services;

import java.util.List;
import net.minecraft.world.inventory.CraftingMenu;

/** Crafts one GrindBook recipe through the 2x2 or 3x3 grid with the shared container transfer channel. Moved out of {@link GrindExecutor} in 2.0.04; it reaches the executor's shared state through {@code g}. */
final class GrindCraftTask implements TaskRunner.Task {
    private final GrindExecutor g;

    private final String item;
    private final int goal;
    private final boolean requiresTable;
    private boolean ingredientClicksQueued;
    private boolean resultClickQueued;
    private int resultWaitTicks;
    private int outputCountBeforeClick;
    private String movementReason;
    private String failureReason;

    GrindCraftTask(GrindExecutor g, String item, int goal, boolean requiresTable) {
        this.g = g;
        this.item = item; this.goal = goal; this.requiresTable = requiresTable;
    }
    @Override public String name() { return "craft " + item + " (" + g.count(item) + "/" + goal + ")"; }
    @Override public boolean satisfied() {
        // The inventory changes optimistically on the client before the final vanilla click
        // reaches the integrated/server connection. Advancing while our transfer still owns
        // that click queue can let the next task or command replace the inventory underneath it.
        return g.count(item) >= goal && !g.inventory.transfers().owns(GrindExecutor.OWNER);
    }
    @Override public int budgetTicks() {
        CraftingPlan.Recipe recipe = GrindBook.recipes().get(item);
        int recipes = recipe == null ? 1 : Math.max(1, Math.ceilDiv(goal - g.count(item), recipe.yield()));
        return Math.min(70_000, Math.max(TaskRunner.DEFAULT_BUDGET_TICKS, recipes * 40 + 2_000));
    }

    @Override public boolean tick() {
        movementReason = null;
        if (failureReason != null) return false;
        if (satisfied()) return true;
        CraftingPlan.Recipe recipe = GrindBook.recipes().get(item);
        if (recipe == null || GrindRecipeLayouts.inputs(item, requiresTable).isEmpty()) {
            failureReason = "no vanilla crafting layout is registered for " + item;
            return false;
        }
        boolean tableMenu = g.client.player.containerMenu instanceof CraftingMenu;
        if (requiresTable && !tableMenu) {
            if (g.stations.closeOwnedStationMenu()) return true;
            movementReason = "Open the nearby crafting table and run .grind resume.";
            return true;
        }
        if (!tableMenu && (g.client.gui.screen() != null || g.client.player.containerMenu != g.client.player.inventoryMenu)) {
            if (g.stations.closeOwnedStationMenu()) return true;
            movementReason = "Close the open screen before AutoGrind can craft " + item + ".";
            return true;
        }
        if (g.inventory.transfers().busy()) {
            if (g.inventory.transfers().recoveryBlocked()) movementReason = g.inventoryRecoveryReason();
            return true;
        }
        int menuId = tableMenu ? g.client.player.containerMenu.containerId : -1;
        boolean table = tableMenu;
        if (ingredientClicksQueued) {
            if (++resultWaitTicks < 3) return true;
            // A station screen can close after only part of its click plan has run. The recipe
            // layout check below sees the cells already placed and queues only the missing ones.
            ingredientClicksQueued = false;
        }
        if (resultClickQueued) {
            if (satisfied()) return true;
            if (g.count(item) <= outputCountBeforeClick) {
                if (++resultWaitTicks > 10 && g.resultItem(menuId, item).equals(item)) {
                    resultClickQueued = false;
                    resultWaitTicks = 0;
                    return true;
                }
                if (resultWaitTicks > 40) {
                    failureReason = "the crafted " + item + " output did not reach the inventory";
                    return false;
                }
                return true;
            }
            resultClickQueued = false;
        }
        if (g.resultItem(menuId, item).equals(item)) {
            if (!g.inventory.transfers().beginClicks(GrindExecutor.OWNER, GrindExecutor.PRIORITY, menuId,
                    new ContainerTransferController.Click[]{new ContainerTransferController.Click(0, 0)}, 0)) {
                return true;
            }
            resultClickQueued = true;
            outputCountBeforeClick = g.count(item);
            resultWaitTicks = 0;
            return true;
        }
        List<ContainerTransferController.Click> clicks = g.craftingInputClicks(item, table, menuId);
        if (clicks == null) {
            failureReason = "missing ingredients or a clear crafting grid for " + item;
            return false;
        }
        if (clicks.isEmpty()) {
            if (++resultWaitTicks > 30) {
                failureReason = "the placed ingredients did not produce the vanilla " + item + " result";
                return false;
            }
            return true;
        }
        if (!g.inventory.transfers().beginClicks(GrindExecutor.OWNER, GrindExecutor.PRIORITY, menuId,
                clicks.toArray(ContainerTransferController.Click[]::new), 0)) return true;
        ingredientClicksQueued = true;
        resultWaitTicks = 0;
        return true;
    }

    @Override public String blockedReason() { return movementReason; }
    @Override public String failureReason() { return failureReason; }
    @Override public void cancel() { g.inventory.transfers().release(GrindExecutor.OWNER); }
}
