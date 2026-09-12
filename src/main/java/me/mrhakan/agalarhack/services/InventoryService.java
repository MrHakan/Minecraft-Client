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
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

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
    private final ContainerTransferController transfers;
    public InventoryService(Minecraft mc, UtilityActionManager actions) {
        this.mc = mc;
        transfers = new ContainerTransferController(new ContainerTransferController.Controls() {
            public boolean ready() {
                return mc.player != null && mc.level != null && mc.player.isAlive()
                        && mc.gui.screen() == null && mc.gameMode != null && mc.player.inventoryMenu != null
                        && mc.player.containerMenu == mc.player.inventoryMenu;
            }
            public boolean cursorEmpty() {
                return mc.player == null || mc.player.inventoryMenu.getCarried().isEmpty();
            }
            public int emptyStorageMenuSlot() {
                if (mc.player == null) return -1;
                int free = mc.player.getInventory().getFreeSlot();
                return free < 0 || free >= InventoryTransfers.INVENTORY_SIZE ? -1 : InventoryTransfers.menuSlot(free);
            }
            public void pickup(int menuSlot) { click(menuSlot, 0, net.minecraft.world.inventory.ContainerInput.PICKUP); }
            public void swap(int menuSlot, int hotbarIndex) { click(menuSlot, hotbarIndex, net.minecraft.world.inventory.ContainerInput.SWAP); }
            public void drop(int menuSlot, boolean wholeStack) {
                // Button 1 throws the whole stack, button 0 a single item.
                click(menuSlot, wholeStack ? 1 : 0, net.minecraft.world.inventory.ContainerInput.THROW);
            }
            private void click(int menuSlot, int button, net.minecraft.world.inventory.ContainerInput input) {
                if (mc.gameMode == null || mc.player == null) return;
                mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, menuSlot, button, input, mc.player);
            }
        }, actions);
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
    /** Inventory-menu click sequences for armour/offhand automation. */
    public ContainerTransferController transfers() { return transfers; }
    public void tick() {
        transfers.tick();
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
    public void reset() { transfers.clear(); leases.clear(); observedPlayer = null; observedSlot = -1; observed.clear(); equipment.clear(); }

    // --- Item scoring adapters -------------------------------------------------------------
    // These translate live 26.2 component data into the pure records in ItemScoring. Attribute
    // values come from the item's own modifiers for the slot it would occupy, so an item that
    // grants nothing in that slot correctly scores as worthless there.

    private static double addedValue(ItemStack stack, EquipmentSlot slot, Holder<Attribute> attribute) {
        double[] total = { 0 };
        stack.forEachModifier(slot, (holder, modifier) -> {
            if (holder == attribute || holder.value() == attribute.value()) {
                // Only flat additions are comparable between candidate items; the multiplied
                // operations depend on the wearer's other gear and are ignored deliberately.
                if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) total[0] += modifier.amount();
            }
        });
        return Double.isFinite(total[0]) ? total[0] : 0;
    }

    /** Single pass over the enchantment component; the level map is small but lookups are frequent. */
    private static int enchantment(ItemStack stack, ResourceKey<Enchantment> key) {
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) return 0;
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (holder.is(key)) return enchantments.getLevel(holder);
        }
        return 0;
    }

    public static double durabilityFraction(ItemStack stack) {
        if (!stack.isDamageableItem()) return 1;
        int max = stack.getMaxDamage();
        return max <= 0 ? 1 : (double) (max - stack.getDamageValue()) / max;
    }

    /** The slot this item is worn in, or null when it is not wearable. */
    public static EquipmentSlot equipmentSlotOf(ItemStack stack) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable == null ? null : equippable.slot();
    }

    public static ItemScoring.ArmorStats armorStats(ItemStack stack, EquipmentSlot slot) {
        if (stack == null || stack.isEmpty()) return null;
        return new ItemScoring.ArmorStats(
                addedValue(stack, slot, Attributes.ARMOR),
                addedValue(stack, slot, Attributes.ARMOR_TOUGHNESS),
                addedValue(stack, slot, Attributes.KNOCKBACK_RESISTANCE),
                enchantment(stack, Enchantments.PROTECTION),
                enchantment(stack, Enchantments.PROJECTILE_PROTECTION),
                enchantment(stack, Enchantments.BLAST_PROTECTION),
                enchantment(stack, Enchantments.FIRE_PROTECTION),
                enchantment(stack, Enchantments.THORNS),
                enchantment(stack, Enchantments.UNBREAKING),
                durabilityFraction(stack), stack.isDamageableItem(),
                stack.has(DataComponents.CUSTOM_NAME));
    }

    public static ItemScoring.WeaponStats weaponStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        // Player base values; item modifiers are deltas applied on top of them.
        double damage = 1 + addedValue(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE);
        double speed = Attributes.DEFAULT_ATTACK_SPEED + addedValue(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_SPEED);
        return new ItemScoring.WeaponStats(damage, speed,
                enchantment(stack, Enchantments.SHARPNESS),
                enchantment(stack, Enchantments.SMITE),
                enchantment(stack, Enchantments.BANE_OF_ARTHROPODS),
                enchantment(stack, Enchantments.FIRE_ASPECT),
                enchantment(stack, Enchantments.KNOCKBACK),
                durabilityFraction(stack), stack.isDamageableItem(),
                stack.has(DataComponents.CUSTOM_NAME));
    }

    /** Which damage-enchantment family applies to this target, using the vanilla entity-type tags. */
    public static ItemScoring.TargetFamily familyOf(LivingEntity target) {
        if (target == null) return ItemScoring.TargetFamily.GENERIC;
        var type = target.getType().builtInRegistryHolder();
        if (type.is(EntityTypeTags.SENSITIVE_TO_SMITE)) return ItemScoring.TargetFamily.UNDEAD;
        if (type.is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) return ItemScoring.TargetFamily.ARTHROPOD;
        return ItemScoring.TargetFamily.GENERIC;
    }

    public double armorScore(ItemStack stack, EquipmentSlot slot) {
        var stats = armorStats(stack, slot);
        return stats == null ? Double.NEGATIVE_INFINITY : ItemScoring.armorScore(stats);
    }

    public double weaponScore(ItemStack stack, ItemScoring.TargetFamily family, double speedWeight) {
        var stats = weaponStats(stack);
        return stats == null ? Double.NEGATIVE_INFINITY : ItemScoring.weaponScore(stats, family, speedWeight);
    }

    /** Best hotbar weapon for the given target, or -1 when nothing scores above bare fists. */
    public int findBestWeapon(ItemScoring.TargetFamily family, double speedWeight, int minimumDurability) {
        if (mc.player == null) return -1;
        double fists = ItemScoring.weaponScore(new ItemScoring.WeaponStats(1, Attributes.DEFAULT_ATTACK_SPEED,
                0, 0, 0, 0, 0, 1, false, false), family, speedWeight);
        return InventorySelection.best(9, fists, slot -> {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) return -1;
            if (stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= minimumDurability) return -1;
            return weaponScore(stack, family, speedWeight);
        });
    }

    /**
     * Best replacement for a worn armour piece. Returns the inventory index of a strictly better
     * candidate, or -1 when the equipped piece already wins.
     */
    public int findBestArmor(EquipmentSlot slot, double minimumImprovement, boolean preserveNamed, boolean storageOnly) {
        if (mc.player == null || slot == null) return -1;
        double current = armorScore(mc.player.getItemBySlot(slot), slot);
        double baseline = Double.isFinite(current) ? current + Math.max(0, minimumImprovement) : Double.NEGATIVE_INFINITY;
        return InventorySelection.best(InventoryTransfers.INVENTORY_SIZE, baseline, index -> {
            if (storageOnly && InventoryTransfers.isHotbarIndex(index)) return -1;
            ItemStack stack = mc.player.getInventory().getItem(index);
            if (stack.isEmpty() || equipmentSlotOf(stack) != slot) return -1;
            if (preserveNamed && stack.has(DataComponents.CUSTOM_NAME)) return -1;
            return armorScore(stack, slot);
        });
    }
}
