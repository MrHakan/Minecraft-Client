package me.mrhakan.agalarhack.services;

import java.util.Objects;
import me.mrhakan.agalarhack.managers.UtilityActionManager;

/**
 * Executes short click sequences against the player's own inventory menu, one click per tick.
 *
 * <p>Container automation is the easiest way to corrupt a player's inventory, so this controller is
 * deliberately conservative:
 * <ul>
 *   <li>only one owner holds the container channel at a time, arbitrated by {@link UtilityActionManager};</li>
 *   <li>a plan only starts when the cursor is empty and the client is in a normal play state;</li>
 *   <li>if the state stops being safe mid-plan the remaining clicks are dropped, never guessed;</li>
 *   <li>anything left on the cursor is returned to a free slot before the channel is released.</li>
 * </ul>
 *
 * <p>The Minecraft-facing operations are behind {@link Controls} so the sequencing, abort and
 * recovery rules are unit tested without a running client.
 */
public final class ContainerTransferController {
    /** Minecraft-facing operations; every method is called on the client thread. */
    public interface Controls {
        /** Play state is normal: world and living player, no screen open, own inventory menu present. */
        boolean ready();
        /** Whether the mouse cursor currently carries a stack. */
        boolean cursorEmpty();
        /** Menu slot id of an empty storage slot, or -1 when the inventory is full. */
        int emptyStorageMenuSlot();
        /** A vanilla PICKUP click on a menu slot. */
        void pickup(int menuSlot);
        /** A vanilla SWAP click exchanging a menu slot with a hotbar index 0..8. */
        void swap(int menuSlot, int hotbarIndex);
    }

    /** Upper bound on a single plan; the longest supported sequence is three clicks. */
    private static final int MAX_PLAN = 3;

    private final Controls controls;
    private final UtilityActionManager actions;
    private String owner;
    private int priority;
    private int[] plan = new int[0];
    private int step;
    private int cooldown;
    private int delay = 1;
    private boolean recovering;

    public ContainerTransferController(Controls controls, UtilityActionManager actions) {
        this.controls = Objects.requireNonNull(controls);
        this.actions = Objects.requireNonNull(actions);
    }

    /**
     * Whether {@code owner} may take the channel now.
     *
     * <p>Callers reach this only with an empty cursor, which is what makes handing the channel over
     * safe: every click boundary with nothing carried leaves the inventory in a consistent state, so
     * an abandoned plan cannot strand a stack. A plan that is still holding a carried item therefore
     * never reports an empty cursor and cannot be preempted, and an owner can never preempt itself.
     */
    private boolean canPreempt(String owner, int priority) {
        if (busy() && !(priority > this.priority && !recovering)) return false;
        return actions.claimContainer(owner, priority);
    }

    /** Ticks between clicks. One tick is already slower than a player's hand. */
    public void setDelay(int ticks) { this.delay = Math.max(0, Math.min(20, ticks)); }

    public boolean busy() { return owner != null; }
    public boolean owns(String owner) { return this.owner != null && this.owner.equals(owner); }

    /**
     * Queues a plan of PICKUP clicks. Returns false when the channel is held by an equal or higher
     * priority owner, when the client is not in a safe state, or when the cursor is already in use.
     */
    public boolean begin(String owner, int priority, int[] menuSlots) {
        if (owner == null || owner.isBlank() || menuSlots == null || menuSlots.length == 0 || menuSlots.length > MAX_PLAN) return false;
        if (!controls.ready() || !controls.cursorEmpty()) return false;
        if (!canPreempt(owner, priority)) return false;
        this.owner = owner;
        this.priority = priority;
        this.plan = menuSlots.clone();
        this.step = 0;
        this.cooldown = 0;
        this.recovering = false;
        return true;
    }

    /**
     * A single atomic hotbar swap. Unlike {@link #begin} this never touches the cursor, so it is the
     * preferred route whenever the source stack already sits in the hotbar.
     */
    public boolean swapHotbar(String owner, int priority, int menuSlot, int hotbarIndex) {
        if (owner == null || owner.isBlank() || hotbarIndex < 0 || hotbarIndex >= InventoryTransfers.HOTBAR_SIZE) return false;
        if (!controls.ready() || !controls.cursorEmpty()) return false;
        if (!canPreempt(owner, priority)) return false;
        finish();
        controls.swap(menuSlot, hotbarIndex);
        return true;
    }

    /** Advances at most one click. Must be called once per client tick. */
    public void tick() {
        if (owner == null) return;
        if (!controls.ready()) {
            // The click channel is gone (screen opened, death, disconnect). Keep ownership so the
            // recovery pass runs as soon as the client is usable again rather than abandoning a stack.
            plan = new int[0];
            step = 0;
            recovering = true;
            return;
        }
        if (cooldown > 0) { cooldown--; return; }
        if (step < plan.length) {
            controls.pickup(plan[step++]);
            cooldown = delay;
            return;
        }
        if (!controls.cursorEmpty()) {
            int free = controls.emptyStorageMenuSlot();
            if (free >= 0) { controls.pickup(free); cooldown = delay; recovering = true; return; }
            // Inventory is full and the stack cannot be put down safely. Hold the channel rather
            // than dropping the item; the next free slot completes the recovery.
            recovering = true;
            return;
        }
        finish();
    }

    /** True while the controller is only holding the channel to put a carried stack back. */
    public boolean recovering() { return recovering; }

    /** Releases the channel unless a carried stack still has to be returned. */
    public void release(String owner) {
        if (!owns(owner)) return;
        if (controls.ready() && !controls.cursorEmpty()) { plan = new int[0]; step = 0; recovering = true; return; }
        finish();
    }

    /** Unconditional teardown for world changes and player replacement. */
    public void clear() {
        owner = null; priority = 0; plan = new int[0]; step = 0; cooldown = 0; recovering = false;
    }

    private void finish() {
        owner = null; priority = 0; plan = new int[0]; step = 0; recovering = false;
    }
}
