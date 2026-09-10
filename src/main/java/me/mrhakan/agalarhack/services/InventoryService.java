package me.mrhakan.agalarhack.services;

import java.util.function.Predicate;
import me.mrhakan.agalarhack.managers.UtilityActionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded local inventory queries and persistent hotbar/use leases.
 * The original player owns the snapshot. Preemption restores before the next owner captures.
 * Manual slot changes cancel the lease and are never overwritten by swap-back.
 */
public final class InventoryService {
    private final Minecraft mc;
    private final UtilityActionManager actions;
    private Lease lease;
    private static final class Lease {
        String owner; int priority, previous, applied;
        LocalPlayer player;
        boolean restore, use, previousUse;
    }
    public InventoryService(Minecraft mc, UtilityActionManager actions) { this.mc = mc; this.actions = actions; }
    public int selectedSlot() { return mc.player == null ? -1 : mc.player.getInventory().getSelectedSlot(); }
    public int findItem(Item item, boolean hotbar) { return find(stack -> stack.is(item), hotbar); }
    public int findBlock(boolean hotbar) { return find(stack -> stack.getItem() instanceof BlockItem, hotbar); }
    public int findPotion(boolean hotbar) { return find(stack -> stack.has(DataComponents.POTION_CONTENTS), hotbar); }
    public int find(Predicate<ItemStack> predicate, boolean hotbar) {
        if (mc.player == null) return -1;
        for (int slot = 0; slot < (hotbar ? 9 : 36); slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) return slot;
        }
        return -1;
    }
    public int findFood(boolean allowGolden) {
        if (mc.player == null) return -1;
        return InventorySelection.best(9, -1, slot -> {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty() || (!allowGolden && (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)))) return -1;
            var food = stack.get(DataComponents.FOOD);
            return food == null ? -1 : food.nutrition();
        });
    }
    public int findBestTool(BlockState state, int minimumDurability) {
        if (mc.player == null) return -1;
        return InventorySelection.best(9, 1, slot -> {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty() || (stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= minimumDurability)) return -1;
            return stack.getDestroySpeed(state) + (stack.isCorrectToolForDrops(state) ? 1000 : 0);
        });
    }
    public boolean owns(String owner) { return lease != null && lease.owner.equals(owner); }
    public void tick() {
        if (lease != null && (mc.player != lease.player || mc.level == null || !lease.player.isAlive()
                || mc.gui.screen() != null || selectedSlot() != lease.applied)) reset();
    }
    public boolean select(String owner, int priority, int slot, boolean use, boolean restore) {
        tick();
        if (owner == null || owner.isBlank() || mc.player == null || mc.gui.screen() != null || slot < 0 || slot > 8) return false;
        if (lease != null && !owns(owner) && priority <= lease.priority) return false;
        if (use ? !actions.claimHotbarAndUse(owner, priority) : !actions.claimHotbar(owner, priority)) return false;
        if (lease != null && !owns(owner)) reset();
        if (lease == null) {
            lease = new Lease(); lease.owner = owner; lease.priority = priority;
            lease.player = mc.player; lease.previous = selectedSlot(); lease.restore = restore;
        }
        if (use && !lease.use) { lease.previousUse = mc.options.keyUse.isDown(); lease.use = true; }
        lease.applied = slot;
        lease.player.getInventory().setSelectedSlot(slot);
        if (use) mc.options.keyUse.setDown(true);
        return true;
    }
    public void release(String owner) { if (owns(owner)) reset(); }
    public void reset() {
        if (lease == null) return;
        Lease previous = lease; lease = null;
        if (previous.use) mc.options.keyUse.setDown(previous.previousUse);
        if (previous.restore && previous.player.getInventory().getSelectedSlot() == previous.applied) {
            previous.player.getInventory().setSelectedSlot(previous.previous);
        }
    }
}
