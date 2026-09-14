package me.mrhakan.agalarhack.services;

/**
 * Slot arithmetic for the player's own inventory menu.
 *
 * <p>Minecraft exposes two different coordinate systems for the same items and confusing them
 * silently moves the wrong stack. {@code Inventory} indices are 0..8 hotbar then 9..35 storage,
 * while {@code InventoryMenu} slot ids are 0 result, 1..4 crafting, 5..8 armour, 9..35 storage,
 * 36..44 hotbar and 45 offhand. Every conversion in the client goes through this class.
 *
 * <p>Constants mirror {@code InventoryMenu} in Minecraft 26.2 and are asserted by unit tests so a
 * future layout change fails the build instead of corrupting a player's inventory.
 */
public final class InventoryTransfers {
    private InventoryTransfers() { }

    public static final int MENU_ARMOR_START = 5;
    public static final int MENU_ARMOR_END = 9;
    public static final int MENU_STORAGE_START = 9;
    public static final int MENU_STORAGE_END = 36;
    public static final int MENU_HOTBAR_START = 36;
    public static final int MENU_HOTBAR_END = 45;
    public static final int MENU_OFFHAND = 45;
    public static final int INVENTORY_SIZE = 36;
    public static final int HOTBAR_SIZE = 9;

    /** Armour menu slots descend from the head, matching the vanilla screen order. */
    public enum ArmorPiece {
        HEAD(0), CHEST(1), LEGS(2), FEET(3);
        private final int order;
        ArmorPiece(int order) { this.order = order; }
        public int menuSlot() { return MENU_ARMOR_START + order; }
    }

    /** Converts an inventory index (0..35) to its player-menu slot id. */
    public static int menuSlot(int inventoryIndex) {
        if (inventoryIndex < 0 || inventoryIndex >= INVENTORY_SIZE) {
            throw new IllegalArgumentException("Inventory index must be 0..35, got " + inventoryIndex);
        }
        return inventoryIndex < HOTBAR_SIZE ? MENU_HOTBAR_START + inventoryIndex : inventoryIndex;
    }

    /** Inverse of {@link #menuSlot(int)}; -1 for armour, offhand, crafting and unknown ids. */
    public static int inventoryIndex(int menuSlot) {
        if (menuSlot >= MENU_HOTBAR_START && menuSlot < MENU_HOTBAR_END) return menuSlot - MENU_HOTBAR_START;
        if (menuSlot >= MENU_STORAGE_START && menuSlot < MENU_STORAGE_END) return menuSlot;
        return -1;
    }

    public static boolean isHotbarIndex(int inventoryIndex) {
        return inventoryIndex >= 0 && inventoryIndex < HOTBAR_SIZE;
    }

    public static boolean isStorageMenuSlot(int menuSlot) {
        return inventoryIndex(menuSlot) >= 0;
    }

    /**
     * Three PICKUP clicks that move {@code sourceIndex} into {@code targetMenuSlot} and return
     * whatever was displaced into the now-empty source slot.
     *
     * <p>The final click is deliberately kept even when the target was empty: clicking a storage
     * slot with an empty cursor is a no-op, so the plan stays a single fixed shape and the
     * controller never has to branch on a state that may change between ticks.
     */
    public static int[] equipPlan(int sourceIndex, int targetMenuSlot) {
        int source = menuSlot(sourceIndex);
        if (source == targetMenuSlot) throw new IllegalArgumentException("Transfer source and target are identical");
        if (targetMenuSlot < 0 || targetMenuSlot > MENU_OFFHAND) {
            throw new IllegalArgumentException("Target menu slot must be 0..45, got " + targetMenuSlot);
        }
        return new int[] { source, targetMenuSlot, source };
    }
}
