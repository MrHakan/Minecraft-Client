package me.mrhakan.agalarhack.services;

import me.mrhakan.agalarhack.managers.UtilityActionManager;

/** Testable slot/use ownership with original-player restoration and manual-input backoff. */
public final class InventoryLeaseController<P> {
    public interface Controls<P> {
        P currentPlayer();
        int selected(P player);
        void select(P player, int slot);
        boolean physicalUseDown();
        void use(boolean down);
    }
    private final Controls<P> controls;
    private final UtilityActionManager actions;
    private P player;
    private String owner;
    private int priority, previous, applied, manualBackoff;
    private boolean restore, use;
    public InventoryLeaseController(Controls<P> controls, UtilityActionManager actions) {
        this.controls = controls; this.actions = actions;
    }
    public boolean owns(String owner) { return this.owner != null && this.owner.equals(owner); }
    public void tick(boolean ready) {
        if (manualBackoff > 0) manualBackoff--;
        if (owner == null) return;
        if (!ready || controls.currentPlayer() != player) { reset(); return; }
        if (controls.selected(player) != applied) { reset(); manualBackoff = 10; }
    }
    public boolean select(String owner, int priority, int slot, boolean use, boolean restore) {
        if (owner == null || owner.isBlank() || controls.currentPlayer() == null || slot < 0 || slot > 8 || manualBackoff > 0) return false;
        if (this.owner != null && !owns(owner) && priority <= this.priority) return false;
        if (use ? !actions.claimHotbarAndUse(owner, priority) : !actions.claimHotbar(owner, priority)) return false;
        if (this.owner != null && !owns(owner)) reset();
        if (this.owner == null) {
            this.owner = owner; this.priority = priority; player = controls.currentPlayer();
            previous = controls.selected(player); this.restore = restore;
        }
        this.priority = Math.max(this.priority, priority);
        // The use key follows this tick's request rather than latching on the first one. Latching
        // made a single-tick pulse into a held right-click: nothing lowered the key again until the
        // lease was released, so AutoFish cast, vanilla re-used the rod four ticks later, and the
        // bobber was retrieved and re-thrown before it ever left the player. A holder that wants the
        // key down keeps asking for it, which is what AutoEat already does while it eats.
        this.use = use;
        applied = slot; controls.select(player, slot);
        // Never lower a key the player is physically holding; reset() has the same rule.
        controls.use(use || controls.physicalUseDown());
        return true;
    }
    public void release(String owner) { if (owns(owner)) reset(); }
    public void reset() {
        if (owner == null) return;
        if (use) controls.use(controls.physicalUseDown());
        if (restore && controls.selected(player) == applied) controls.select(player, previous);
        owner = null; player = null; use = false;
    }
    public void clear() { reset(); manualBackoff = 0; }
}
