package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryTransfersTest {
    /** These mirror InventoryMenu in Minecraft 26.2; a layout change must fail here, not in a player's inventory. */
    @Test void menuLayoutMatchesVanilla() {
        assertEquals(5, InventoryTransfers.MENU_ARMOR_START);
        assertEquals(9, InventoryTransfers.MENU_ARMOR_END);
        assertEquals(9, InventoryTransfers.MENU_STORAGE_START);
        assertEquals(36, InventoryTransfers.MENU_STORAGE_END);
        assertEquals(36, InventoryTransfers.MENU_HOTBAR_START);
        assertEquals(45, InventoryTransfers.MENU_OFFHAND);
        assertEquals(5, InventoryTransfers.ArmorPiece.HEAD.menuSlot());
        assertEquals(6, InventoryTransfers.ArmorPiece.CHEST.menuSlot());
        assertEquals(7, InventoryTransfers.ArmorPiece.LEGS.menuSlot());
        assertEquals(8, InventoryTransfers.ArmorPiece.FEET.menuSlot());
    }

    @Test void hotbarIndicesMapToTheBottomRow() {
        assertEquals(36, InventoryTransfers.menuSlot(0));
        assertEquals(44, InventoryTransfers.menuSlot(8));
    }

    @Test void storageIndicesKeepTheirId() {
        assertEquals(9, InventoryTransfers.menuSlot(9));
        assertEquals(35, InventoryTransfers.menuSlot(35));
    }

    @Test void conversionRoundTripsForEveryInventoryIndex() {
        for (int index = 0; index < InventoryTransfers.INVENTORY_SIZE; index++) {
            assertEquals(index, InventoryTransfers.inventoryIndex(InventoryTransfers.menuSlot(index)));
        }
    }

    @Test void nonStorageMenuSlotsHaveNoInventoryIndex() {
        for (int slot : new int[] { 0, 1, 4, 5, 8, 45 }) {
            assertEquals(-1, InventoryTransfers.inventoryIndex(slot), "menu slot " + slot);
            assertFalse(InventoryTransfers.isStorageMenuSlot(slot));
        }
    }

    @Test void outOfRangeIndicesAreRejectedRatherThanWrapped() {
        assertThrows(IllegalArgumentException.class, () -> InventoryTransfers.menuSlot(-1));
        assertThrows(IllegalArgumentException.class, () -> InventoryTransfers.menuSlot(36));
    }

    @Test void equipPlanReturnsDisplacedItemToTheSourceSlot() {
        assertArrayEquals(new int[] { 36, 5, 36 }, InventoryTransfers.equipPlan(0, 5));
        assertArrayEquals(new int[] { 20, 45, 20 }, InventoryTransfers.equipPlan(20, InventoryTransfers.MENU_OFFHAND));
    }

    @Test void equipPlanRejectsSelfTransfersAndBadTargets() {
        assertThrows(IllegalArgumentException.class, () -> InventoryTransfers.equipPlan(0, 36));
        assertThrows(IllegalArgumentException.class, () -> InventoryTransfers.equipPlan(0, 46));
        assertThrows(IllegalArgumentException.class, () -> InventoryTransfers.equipPlan(0, -1));
    }

    @Test void hotbarDetectionCoversOnlyTheFirstNineIndices() {
        assertTrue(InventoryTransfers.isHotbarIndex(0));
        assertTrue(InventoryTransfers.isHotbarIndex(8));
        assertFalse(InventoryTransfers.isHotbarIndex(9));
        assertFalse(InventoryTransfers.isHotbarIndex(-1));
    }
}
