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
    /**
     * A single-tick pulse must be a single click.
     *
     * <p>This latched: the first tick that asked for the use key pressed it, and nothing lowered it
     * again until the lease was released. AutoFish therefore held right-click instead of clicking
     * once, and the game re-used the rod every few ticks - casting, retrieving and recasting so the
     * bobber never left the player. Found by the AutoFish game test scenario, whose hook changed
     * entity id five times in forty ticks without moving.
     */
    @Test void aPulseThatIsNotRepeatedReleasesTheUseKey() {
        var controls = new Controls(); var actions = new UtilityActionManager();
        var leases = new InventoryLeaseController<>(controls, actions);
        assertTrue(leases.select("fish",40,1,true,true));
        assertTrue(controls.use, "the tick that asked for use should press the key");
        actions.beginTick(); leases.tick(true);
        assertTrue(leases.select("fish",40,1,false,true));
        assertFalse(controls.use, "a tick that does not ask for use must release the key again");
    }

    /** Holding is still holding: a module that asks every tick keeps the key down. */
    @Test void repeatedRequestsKeepTheUseKeyDown() {
        var controls = new Controls(); var actions = new UtilityActionManager();
        var leases = new InventoryLeaseController<>(controls, actions);
        for (int tick = 0; tick < 5; tick++) {
            actions.beginTick(); leases.tick(true);
            assertTrue(leases.select("food",60,3,true,true));
            assertTrue(controls.use);
        }
    }

    /** A key the player is physically holding is never lowered by a lease that stops asking. */
    @Test void aLeaseNeverLowersTheKeyThePlayerIsHolding() {
        var controls = new Controls(); var actions = new UtilityActionManager();
        var leases = new InventoryLeaseController<>(controls, actions);
        controls.physical = true;
        assertTrue(leases.select("fish",40,1,true,true));
        actions.beginTick(); leases.tick(true);
        assertTrue(leases.select("fish",40,1,false,true));
        assertTrue(controls.use, "the player's own right-click must survive the lease letting go");
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
