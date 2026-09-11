package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.List;
import me.mrhakan.agalarhack.managers.UtilityActionManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContainerTransferControllerTest {
    static final class FakeControls implements ContainerTransferController.Controls {
        boolean ready = true;
        boolean cursorEmpty = true;
        int freeSlot = 30;
        final List<String> clicks = new ArrayList<>();
        public boolean ready() { return ready; }
        public boolean cursorEmpty() { return cursorEmpty; }
        public int emptyStorageMenuSlot() { return freeSlot; }
        public void pickup(int menuSlot) { clicks.add("pickup:" + menuSlot); }
        public void swap(int menuSlot, int hotbarIndex) { clicks.add("swap:" + menuSlot + ":" + hotbarIndex); }
    }

    private static ContainerTransferController controller(FakeControls controls) {
        var actions = new UtilityActionManager();
        var controller = new ContainerTransferController(controls, actions);
        controller.setDelay(0);
        return controller;
    }

    @Test void planRunsOneClickPerTickThenReleases() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertTrue(controller.begin("autoarmor", 50, InventoryTransfers.equipPlan(10, 6)));
        controller.tick(); controller.tick(); controller.tick();
        assertEquals(List.of("pickup:10", "pickup:6", "pickup:10"), controls.clicks);
        assertTrue(controller.busy());
        controller.tick();
        assertFalse(controller.busy());
    }

    @Test void delaySpacesOutClicks() {
        var controls = new FakeControls();
        var actions = new UtilityActionManager();
        var controller = new ContainerTransferController(controls, actions);
        controller.setDelay(2);
        assertTrue(controller.begin("autoarmor", 50, new int[] { 10, 6 }));
        controller.tick();
        controller.tick(); controller.tick();
        assertEquals(1, controls.clicks.size());
        controller.tick();
        assertEquals(2, controls.clicks.size());
    }

    @Test void aPlanNeverStartsWithAStackOnTheCursor() {
        var controls = new FakeControls();
        controls.cursorEmpty = false;
        var controller = controller(controls);
        assertFalse(controller.begin("autoarmor", 50, new int[] { 10, 6 }));
        assertTrue(controls.clicks.isEmpty());
    }

    @Test void aPlanNeverStartsWhileAScreenIsOpen() {
        var controls = new FakeControls();
        controls.ready = false;
        var controller = controller(controls);
        assertFalse(controller.begin("autoarmor", 50, new int[] { 10, 6 }));
    }

    @Test void anEqualOrLowerPriorityOwnerCannotTakeTheChannel() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertTrue(controller.begin("autototem", 90, new int[] { 11, 45 }));
        assertFalse(controller.begin("autoarmor", 50, new int[] { 10, 6 }));
        assertFalse(controller.begin("other", 90, new int[] { 10, 6 }));
    }

    @Test void aHigherPriorityOwnerPreemptsAnIdleCursorPlan() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertTrue(controller.begin("autoarmor", 50, InventoryTransfers.equipPlan(10, 6)));
        assertTrue(controller.begin("autototem", 90, new int[] { 11, 45 }));
        assertTrue(controller.owns("autototem"));
        controller.tick(); controller.tick();
        assertEquals(List.of("pickup:11", "pickup:45"), controls.clicks);
    }

    @Test void preemptionNeverStrandsACarriedStack() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertTrue(controller.begin("autoarmor", 50, InventoryTransfers.equipPlan(10, 6)));
        controller.tick();
        controls.cursorEmpty = false;
        assertFalse(controller.begin("autototem", 90, new int[] { 11, 45 }));
        assertFalse(controller.swapHotbar("autototem", 90, InventoryTransfers.MENU_OFFHAND, 2));
        assertTrue(controller.owns("autoarmor"));
    }

    @Test void aModuleCannotPreemptItself() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertTrue(controller.begin("autototem", 90, InventoryTransfers.equipPlan(11, 45)));
        controller.tick();
        assertFalse(controller.begin("autototem", 90, InventoryTransfers.equipPlan(12, 45)));
        controller.tick(); controller.tick();
        assertEquals(List.of("pickup:11", "pickup:45", "pickup:11"), controls.clicks);
    }

    @Test void recoveringOwnershipIsNeverPreempted() {
        var controls = new FakeControls();
        controls.freeSlot = -1;
        var controller = controller(controls);
        assertTrue(controller.begin("autoarmor", 50, new int[] { 10 }));
        controller.tick();
        controls.cursorEmpty = false;
        controller.tick();
        assertTrue(controller.recovering());
        controls.cursorEmpty = true;
        assertFalse(controller.begin("autototem", 90, new int[] { 11, 45 }));
    }

    @Test void losingTheClickChannelDropsRemainingClicksInsteadOfGuessing() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertTrue(controller.begin("autoarmor", 50, InventoryTransfers.equipPlan(10, 6)));
        controller.tick();
        controls.ready = false;
        controls.cursorEmpty = false;
        controller.tick(); controller.tick();
        assertEquals(List.of("pickup:10"), controls.clicks);
        assertTrue(controller.busy());
        assertTrue(controller.recovering());
    }

    @Test void carriedStackIsReturnedToAFreeSlotWhenTheClientRecovers() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertTrue(controller.begin("autoarmor", 50, InventoryTransfers.equipPlan(10, 6)));
        controller.tick();
        controls.ready = false; controls.cursorEmpty = false;
        controller.tick();
        controls.ready = true;
        controller.tick();
        assertEquals(List.of("pickup:10", "pickup:30"), controls.clicks);
        controls.cursorEmpty = true;
        controller.tick();
        assertFalse(controller.busy());
    }

    @Test void aFullInventoryHoldsTheChannelRatherThanDroppingTheStack() {
        var controls = new FakeControls();
        controls.freeSlot = -1;
        var controller = controller(controls);
        assertTrue(controller.begin("autoarmor", 50, new int[] { 10 }));
        controller.tick();
        controls.cursorEmpty = false;
        controller.tick(); controller.tick();
        assertEquals(List.of("pickup:10"), controls.clicks);
        assertTrue(controller.busy());
        controls.freeSlot = 31;
        controller.tick();
        assertEquals(List.of("pickup:10", "pickup:31"), controls.clicks);
    }

    @Test void releaseWaitsForTheCursorToBeEmptied() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertTrue(controller.begin("autoarmor", 50, new int[] { 10 }));
        controller.tick();
        controls.cursorEmpty = false;
        controller.release("autoarmor");
        assertTrue(controller.busy());
        controls.cursorEmpty = true;
        controller.release("autoarmor");
        assertFalse(controller.busy());
    }

    @Test void hotbarSwapIsASingleClickAndNeverTouchesTheCursor() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertTrue(controller.swapHotbar("autototem", 90, InventoryTransfers.MENU_OFFHAND, 4));
        assertEquals(List.of("swap:45:4"), controls.clicks);
        assertFalse(controller.busy());
    }

    @Test void hotbarSwapValidatesTheHotbarIndex() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertFalse(controller.swapHotbar("autototem", 90, InventoryTransfers.MENU_OFFHAND, 9));
        assertFalse(controller.swapHotbar("autototem", 90, InventoryTransfers.MENU_OFFHAND, -1));
        assertTrue(controls.clicks.isEmpty());
    }

    @Test void clearAbandonsEverythingForWorldChanges() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertTrue(controller.begin("autoarmor", 50, new int[] { 10, 6 }));
        controller.clear();
        assertFalse(controller.busy());
        assertFalse(controller.recovering());
    }

    @Test void oversizedAndEmptyPlansAreRejected() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertFalse(controller.begin("autoarmor", 50, new int[0]));
        assertFalse(controller.begin("autoarmor", 50, new int[] { 1, 2, 3, 4 }));
        assertFalse(controller.begin("autoarmor", 50, null));
        assertFalse(controller.begin(" ", 50, new int[] { 10 }));
    }
}
