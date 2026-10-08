package me.mrhakan.agalarhack.services;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Moves an ingredient out of the offhand so a crafting or smelting click plan can see every input. Moved out of {@link GrindExecutor} in 2.0.04; it reaches the executor's shared state through {@code g}. */
/** Moves relevant offhand ingredients into the normal inventory before opening a station menu. */
final class GrindOffhandTask implements TaskRunner.Task {
    private final GrindExecutor g;

    private final Set<String> relevant;
    private String movementReason;
    private String failureReason;

    GrindOffhandTask(GrindExecutor g, Set<String> relevant) {
        this.g = g;
        this.relevant = relevant;
    }
    @Override public String name() { return "prepare crafting inputs"; }
    @Override public boolean satisfied() {
        ItemStack stack = g.inventory.equipped(EquipmentSlot.OFFHAND);
        return stack.isEmpty() || !relevant.contains(GrindBook.generic(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
    }

    @Override public boolean tick() {
        movementReason = null;
        if (satisfied()) return true;
        if (g.closeOwnedStationMenu()) return true;
        if (g.client.gui.screen() != null || g.client.player.containerMenu != g.client.player.inventoryMenu) {
            movementReason = "Close the open screen before AutoGrind can move an offhand ingredient.";
            return true;
        }
        if (g.inventory.transfers().busy()) return true;
        int destination = g.findEmptyHotbarSlot();
        if (destination < 0) destination = Math.max(0, g.inventory.selectedSlot());
        if (!g.inventory.transfers().begin(GrindExecutor.OWNER, GrindExecutor.PRIORITY, new int[]{InventoryTransfers.MENU_OFFHAND,
                InventoryTransfers.menuSlot(destination), InventoryTransfers.MENU_OFFHAND}, 0)) return true;
        return true;
    }
    @Override public String blockedReason() { return movementReason; }
    @Override public String failureReason() { return failureReason; }
    @Override public void cancel() { g.inventory.transfers().release(GrindExecutor.OWNER); }
}
