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
    private LocalPlayer observedPlayer;
    private final ItemStack[] observed = new ItemStack[36];
    private int observedSlot = -1;
    private final InventoryLeaseController<LocalPlayer> leases;
    public InventoryService(Minecraft mc, UtilityActionManager actions) {
        this.mc = mc;
        leases = new InventoryLeaseController<>(new InventoryLeaseController.Controls<LocalPlayer>() {
            public LocalPlayer currentPlayer() { return mc.player; }
            public int selected(LocalPlayer player) { return player.getInventory().getSelectedSlot(); }
            public void select(LocalPlayer player, int slot) { player.getInventory().setSelectedSlot(slot); }
            public void use(boolean down) { mc.options.keyUse.setDown(down); }
            public boolean physicalUseDown() {
                var key = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(mc.options.keyUse);
                if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
                    return org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                }
                return key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM && key.getValue() >= 0
                        && com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), key.getValue());
            }
        }, actions);
    }
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
    public boolean owns(String owner) { return leases.owns(owner); }
    public void tick() {
        leases.tick(mc.player != null && mc.level != null && mc.player.isAlive() && mc.gui.screen() == null);
        if (observedPlayer != mc.player) {
            observedPlayer = mc.player; java.util.Arrays.fill(observed, null); observedSlot = -1;
        }
        if (observedPlayer == null) return;
        var events = ClientServices.require(me.mrhakan.agalarhack.events.EventBus.class);
        for (int slot = 0; slot < observed.length; slot++) {
            ItemStack current = observedPlayer.getInventory().getItem(slot);
            ItemStack previous = observed[slot];
            if (previous == null || !ItemStack.matches(previous, current)) {
                observed[slot] = current.copy();
                events.post(new me.mrhakan.agalarhack.events.ClientEvents.InventoryUpdated(observedPlayer, slot,
                        previous == null ? ItemStack.EMPTY : previous.copy(), current.copy()));
            }
        }
        int selected = selectedSlot();
        if (observedSlot != selected) {
            int previous = observedSlot; observedSlot = selected;
            events.post(new me.mrhakan.agalarhack.events.ClientEvents.SelectedSlotChanged(previous, selected));
        }
    }
    public boolean select(String owner, int priority, int slot, boolean use, boolean restore) {
        if (mc.player == null || mc.level == null || !mc.player.isAlive() || mc.gui.screen() != null) return false;
        return leases.select(owner, priority, slot, use, restore);
    }
    public void release(String owner) { leases.release(owner); }
    public void reset() { leases.clear(); observedPlayer = null; observedSlot = -1; java.util.Arrays.fill(observed, null); }
}
