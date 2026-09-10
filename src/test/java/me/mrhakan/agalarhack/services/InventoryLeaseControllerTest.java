package me.mrhakan.agalarhack.services;
import me.mrhakan.agalarhack.managers.UtilityActionManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InventoryLeaseControllerTest {
    static class Player { int slot; }
    static class Controls implements InventoryLeaseController.Controls<Player> {
        Player player = new Player(); boolean use, physical;
        public Player currentPlayer() { return player; }
        public int selected(Player p) { return p.slot; }
        public void select(Player p, int slot) { p.slot = slot; }
        public boolean physicalUseDown() { return physical; }
        public void use(boolean value) { use = value; }
    }
    @Test void preemptionRestoresBeforeCapturingNextLease() {
        var controls = new Controls(); var actions = new UtilityActionManager();
        var leases = new InventoryLeaseController<>(controls, actions);
        assertTrue(leases.select("tool",40,2,false,true));
        assertTrue(leases.select("food",60,3,true,true));
        assertFalse(leases.select("tool",40,2,false,true));
        leases.release("tool"); assertEquals(3,controls.player.slot);
        leases.release("food"); assertEquals(0,controls.player.slot); assertFalse(controls.use);
    }
    @Test void manualSlotChangeWinsAndBacksOff() {
        var controls = new Controls(); var actions = new UtilityActionManager();
        var leases = new InventoryLeaseController<>(controls, actions);
        leases.select("food",60,3,true,true);
        controls.player.slot = 5; leases.tick(true);
        assertEquals(5, controls.player.slot); assertFalse(controls.use);
        assertFalse(leases.select("food",60,3,true,true));
        for (int i=0;i<10;i++) { actions.beginTick(); leases.tick(true); }
        assertTrue(leases.select("food",60,3,true,true));
    }
    @Test void replacementRestoresOldPlayerAndPhysicalInput() {
        var controls = new Controls(); var actions = new UtilityActionManager();
        var leases = new InventoryLeaseController<>(controls, actions);
        var original = controls.player;
        leases.select("food",60,3,true,true);
        controls.player = new Player(); controls.player.slot = 7; controls.physical = true;
        leases.tick(true);
        assertEquals(0, original.slot); assertEquals(7,controls.player.slot); assertTrue(controls.use);
    }
}
