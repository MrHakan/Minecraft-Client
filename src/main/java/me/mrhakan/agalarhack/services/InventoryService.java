package me.mrhakan.agalarhack.services;

import java.util.function.Predicate;
import me.mrhakan.agalarhack.managers.UtilityActionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
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
    private static final EquipmentSlot[] EQUIPMENT = { EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND };
    private final SlotSnapshots<ItemStack> observed = new SlotSnapshots<>(36, ItemStack::matches, ItemStack::copy);
    private final SlotSnapshots<ItemStack> equipment = new SlotSnapshots<>(EQUIPMENT.length, ItemStack::matches, ItemStack::copy);
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
    /** Defensive snapshots; inventory indices are 0..35, not container-menu slot IDs. */
    public ItemStack stackAt(int slot) {
        if (slot < 0 || slot >= 36) throw new IllegalArgumentException("Inventory slot must be 0..35");
        return mc.player == null ? ItemStack.EMPTY : mc.player.getInventory().getItem(slot).copy();
    }
    public ItemStack equipped(EquipmentSlot slot) {
        java.util.Objects.requireNonNull(slot);
        return mc.player == null ? ItemStack.EMPTY : mc.player.getItemBySlot(slot).copy();
    }
    public int findHotbar(Predicate<ItemStack> predicate) { return find(predicate, true); }
    public int findInventory(Predicate<ItemStack> predicate) { return find(predicate, false); }
    public int findFood(boolean allowGolden) { return findFood(allowGolden, true); }
    public int findFood(boolean allowGolden, boolean hotbar) {
        if (mc.player == null) return -1;
        return InventorySelection.best(hotbar ? 9 : 36, -1, slot -> {
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
            observedPlayer = mc.player; observed.clear(); equipment.clear(); observedSlot = -1;
        }
        if (observedPlayer == null) return;
        var events = ClientServices.require(me.mrhakan.agalarhack.events.EventBus.class);
        LocalPlayer player = observedPlayer;
        for (int slot = 0; slot < 36; slot++) {
            var change = observed.update(slot, player.getInventory().getItem(slot));
            if (change != null) events.post(new me.mrhakan.agalarhack.events.ClientEvents.InventoryUpdated(player, slot,
                    change.previous() == null ? ItemStack.EMPTY : change.previous(), change.current()));
            if (observedPlayer != player || mc.player != player) return;
        }
        for (int index = 0; index < EQUIPMENT.length; index++) {
            var slot = EQUIPMENT[index];
            var change = equipment.update(index, player.getItemBySlot(slot));
            if (change != null) events.post(new me.mrhakan.agalarhack.events.ClientEvents.EquipmentUpdated(player, slot,
                    change.previous() == null ? ItemStack.EMPTY : change.previous(), change.current()));
            if (observedPlayer != player || mc.player != player) return;
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
    public void reset() { leases.clear(); observedPlayer = null; observedSlot = -1; observed.clear(); equipment.clear(); }
}
