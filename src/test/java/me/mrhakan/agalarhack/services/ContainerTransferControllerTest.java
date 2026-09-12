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
        public void drop(int menuSlot, boolean wholeStack) { clicks.add("drop:" + menuSlot + ":" + wholeStack); }
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

    @Test void disablingWhileAScreenIsOpenRetainsRecoveryUntilPlayResumes() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertTrue(controller.begin("autoarmor", 50, InventoryTransfers.equipPlan(10, 6)));
        controller.tick();
        controls.cursorEmpty = false;
        controls.ready = false;
        controller.release("autoarmor");
        assertTrue(controller.owns("autoarmor"));
        assertTrue(controller.recovering());
        controller.tick();
        assertEquals(List.of("pickup:10"), controls.clicks);
        controls.ready = true;
        controller.tick();
        assertEquals(List.of("pickup:10", "pickup:30"), controls.clicks);
        controls.cursorEmpty = true;
        controller.tick();
        assertFalse(controller.busy());
    }

    @Test void worldTeardownCancelsDeferredRecoveryWithoutClickingTheNewInventory() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertTrue(controller.begin("autoarmor", 50, new int[]{10}));
        controller.tick();
        controls.ready = false;
        controls.cursorEmpty = false;
        controller.release("autoarmor");
        controller.clear();
        controls.ready = true;
        controller.tick();
        assertFalse(controller.busy());
        assertEquals(List.of("pickup:10"), controls.clicks);
    }

    @Test void invalidFinalSlotCannotStartAPartialPlanOrDropOutsideTheWindow() {
        var controls = new FakeControls();
        var controller = controller(controls);
        for (int invalid : new int[]{-999, -1, 46, Integer.MAX_VALUE}) {
            assertFalse(controller.begin("autoarmor", 50, new int[]{10, 6, invalid}));
            assertFalse(controller.swapHotbar("autototem", 90, invalid, 0));
        }
        controller.tick();
        assertFalse(controller.busy());
        assertTrue(controls.clicks.isEmpty());
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

    @Test void atomicClicksShareOneBudgetRegardlessOfOwnerOrPriority() {
        for (boolean swapFirst : new boolean[]{false, true}) {
            var controls = new FakeControls();
            var actions = new UtilityActionManager();
            var controller = new ContainerTransferController(controls, actions);
            actions.beginTick();
            controller.tick();
            assertTrue(swapFirst ? controller.swapHotbar("first", 10, 45, 2)
                    : controller.dropSlot("first", 10, 14, true));
            assertFalse(controller.busy(), "an atomic click needs no carried-stack owner");
            assertFalse(controller.swapHotbar("first", 10, 45, 2), "same-owner swap repeated a click");
            assertFalse(controller.dropSlot("first", 10, 14, true), "same-owner drop repeated a click");
            assertFalse(controller.swapHotbar("urgent", 90, 45, 3), "priority bypassed the click budget");
            assertFalse(controller.dropSlot("urgent", 90, 15, true), "priority bypassed the drop budget");
            assertEquals(1, controls.clicks.size());
            actions.beginTick();
            controller.tick();
            assertTrue(controller.swapHotbar("urgent", 90, 45, 3), "next tick must admit a fresh click");
            assertEquals(2, controls.clicks.size());
        }
    }

    @Test void releasingAPlanAfterDepositDoesNotPermitAnotherClickThatTick() {
        var controls = new FakeControls();
        var actions = new UtilityActionManager();
        var controller = new ContainerTransferController(controls, actions);
        controller.setDelay(0);
        assertTrue(controller.begin("autoarmor", 50, new int[]{10, 6}));
        controller.tick();
        controls.cursorEmpty = false; // The first PICKUP carries the helmet.
        actions.beginTick();
        controller.tick();
        controls.cursorEmpty = true; // The second PICKUP deposited it in the empty armour slot.
        controller.release("autoarmor");
        assertFalse(controller.busy());
        assertFalse(controller.swapHotbar("autototem", 90, 45, 2), "deposit already used this tick");
        assertFalse(controller.dropSlot("cleaner", 100, 14, true), "release cannot replenish clicks");
        assertEquals(List.of("pickup:10", "pickup:6"), controls.clicks);
        actions.beginTick();
        controller.tick();
        assertTrue(controller.swapHotbar("autototem", 90, 45, 2));
    }

    @Test void recoveryUsesTheClickBudgetButWorldTeardownClearsIt() {
        var controls = new FakeControls();
        var actions = new UtilityActionManager();
        var controller = new ContainerTransferController(controls, actions);
        controller.setDelay(0);
        assertTrue(controller.begin("autoarmor", 50, new int[]{10}));
        controller.tick();
        controls.cursorEmpty = false;
        controller.release("autoarmor");
        actions.beginTick();
        controller.tick();
        controls.cursorEmpty = true; // Recovery returned the carried stack to slot 30.
        controller.release("autoarmor");
        assertFalse(controller.swapHotbar("autototem", 90, 45, 2), "recovery already clicked");
        assertFalse(controller.dropSlot("cleaner", 100, 14, true), "recovery budget must survive release");
        assertEquals(List.of("pickup:10", "pickup:30"), controls.clicks);
        controller.clear(); // Unconditional teardown must not burden a replacement inventory.
        actions.beginTick();
        assertTrue(controller.swapHotbar("new-world", 90, 45, 2));
    }

    @Test void rejectedAtomicRequestsDoNotSpendTheClickBudget() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertFalse(controller.swapHotbar("autototem", 90, -999, 2));
        controls.ready = false;
        assertFalse(controller.dropSlot("cleaner", 10, 14, true));
        controls.ready = true;
        controls.cursorEmpty = false;
        assertFalse(controller.swapHotbar("autototem", 90, 45, 2));
        controls.cursorEmpty = true;
        assertTrue(controller.dropSlot("cleaner", 10, 14, true));
        assertEquals(List.of("drop:14:true"), controls.clicks);
    }

    @Test void dropIsASingleClickAndNeverTouchesTheCursor() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertTrue(controller.dropSlot("cleaner", 20, 14, true));
        assertEquals(List.of("drop:14:true"), controls.clicks);
        assertFalse(controller.busy());
    }

    @Test void dropIsRefusedWhileAStackIsCarried() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertTrue(controller.begin("autoarmor", 50, InventoryTransfers.equipPlan(10, 6)));
        controller.tick();
        controls.cursorEmpty = false;
        assertFalse(controller.dropSlot("cleaner", 99, 14, true));
        assertEquals(List.of("pickup:10"), controls.clicks);
    }

    @Test void dropValidatesTheSlotRange() {
        var controls = new FakeControls();
        var controller = controller(controls);
        assertFalse(controller.dropSlot("cleaner", 20, -1, true));
        assertFalse(controller.dropSlot("cleaner", 20, 46, true));
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
