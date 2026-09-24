package me.mrhakan.agalarhack.services;

import java.util.Objects;
import me.mrhakan.agalarhack.managers.UtilityActionManager;

/**
 * Executes owned vanilla-container click sequences, one click per tick.
 *
 * <p>Only one owner holds the channel at a time. Plans start with an empty cursor and a valid
 * player or station menu; losing that menu drops the untrusted remainder and recovers a carried
 * stack through the player's inventory when it is safe. Priority preemption never strands a cursor
 * stack, and a full inventory holds recovery rather than dropping an item. The click plan itself is
 * bounded and unit tested without a running client.
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
        /** A vanilla left-click PICKUP on a menu slot. */
        void pickup(int menuSlot);
        /** A vanilla SWAP click exchanging a menu slot with a hotbar index 0..8. */
        void swap(int menuSlot, int hotbarIndex);
        /** A vanilla THROW click dropping one item, or the whole stack, out of a menu slot. */
        void drop(int menuSlot, boolean wholeStack);

        /** Whether a captured non-player menu is still the active, expected click target. */
        default boolean ready(int containerId) { return ready(); }
        /** Whether that menu's carried stack is empty. */
        default boolean cursorEmpty(int containerId) { return cursorEmpty(); }
        /** Clickable player-inventory slot for cursor recovery, or -1 when none is safe. */
        default int returnStorageMenuSlot(int containerId) { return emptyStorageMenuSlot(); }
        /** One left/right vanilla PICKUP click in the bound menu. */
        default void pickup(int containerId, int menuSlot, int button) {
            if (button != 0) throw new UnsupportedOperationException("right-click pickup is not supported by this control");
            pickup(menuSlot);
        }
    }

    /** Existing equipment transfers stay short; crafting layouts use a separately bounded queue. */
    private static final int MAX_PLAN = 3;
    private static final int MAX_CLICK_PLAN = 256;

    /** A vanilla PICKUP click. Button 0 takes/places a stack; button 1 places one item. */
    public record Click(int menuSlot, int button) {
        public Click {
            if (menuSlot < 0 || menuSlot > InventoryTransfers.MENU_OFFHAND) {
                throw new IllegalArgumentException("Menu slot must be 0..45, got " + menuSlot);
            }
            if (button < 0 || button > 1) throw new IllegalArgumentException("Pickup button must be 0 or 1");
        }
    }

    private final Controls controls;
    private final UtilityActionManager actions;
    private String owner;
    private int priority;
    private Click[] plan = new Click[0];
    private int step;
    /** -1 means the ordinary player inventory menu. */
    private int containerId = -1;
    private int cooldown;
    private int delay = 1;
    private int planDelay = 1;
    private boolean recovering;
    /** True only when cursor recovery cannot make progress without an external change. */
    private boolean recoveryBlocked;
    // Ownership may end or change after a click; neither gives the current tick another click.
    private boolean clickedThisTick;

    public ContainerTransferController(Controls controls, UtilityActionManager actions) {
        this.controls = Objects.requireNonNull(controls);
        this.actions = Objects.requireNonNull(actions);
    }

    private boolean canPreempt(String owner, int priority) {
        if (busy() && !(priority > this.priority && !recovering)) return false;
        return actions.claimContainer(owner, priority);
    }

    public void setDelay(int ticks) { this.delay = clampDelay(ticks); }
    private static int clampDelay(int ticks) { return Math.max(0, Math.min(20, ticks)); }
    public boolean busy() { return owner != null; }
    public boolean owns(String owner) { return this.owner != null && this.owner.equals(owner); }

    /** Queues a short plan of ordinary left-click inventory transfers. */
    public boolean begin(String owner, int priority, int[] menuSlots) {
        return begin(owner, priority, menuSlots, delay);
    }

    /** Queues a short plan paced by its owner rather than another module's last setting. */
    public boolean begin(String owner, int priority, int[] menuSlots, int delayTicks) {
        if (menuSlots == null || menuSlots.length == 0 || menuSlots.length > MAX_PLAN) return false;
        Click[] clicks = new Click[menuSlots.length];
        try {
            for (int i = 0; i < menuSlots.length; i++) clicks[i] = new Click(menuSlots[i], 0);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        return beginClicks(owner, priority, -1, clicks, delayTicks);
    }

    /**
     * Queues a bounded recipe or container transfer in a captured vanilla menu. The same ownership,
     * cursor checks, one-click-per-tick budget, and recovery path used by inventory modules apply.
     */
    public boolean beginClicks(String owner, int priority, int containerId, Click[] clicks, int delayTicks) {
        if (owner == null || owner.isBlank() || containerId < -1 || clicks == null
                || clicks.length == 0 || clicks.length > MAX_CLICK_PLAN) return false;
        for (Click click : clicks) if (click == null) return false;
        if (!controls.ready(containerId) || !controls.cursorEmpty(containerId)) return false;
        if (!canPreempt(owner, priority)) return false;
        this.owner = owner;
        this.priority = priority;
        this.plan = clicks.clone();
        this.step = 0;
        this.containerId = containerId;
        this.cooldown = 0;
        this.planDelay = clampDelay(delayTicks);
        this.recovering = false;
        this.recoveryBlocked = false;
        return true;
    }

    /** One atomic hotbar swap that never touches the cursor. */
    public boolean swapHotbar(String owner, int priority, int menuSlot, int hotbarIndex) {
        if (clickedThisTick || owner == null || owner.isBlank()
                || hotbarIndex < 0 || hotbarIndex >= InventoryTransfers.HOTBAR_SIZE
                || menuSlot < 0 || menuSlot > InventoryTransfers.MENU_OFFHAND) return false;
        if (!controls.ready() || !controls.cursorEmpty() || !canPreempt(owner, priority)) return false;
        finish();
        clickedThisTick = true;
        controls.swap(menuSlot, hotbarIndex);
        return true;
    }

    /** One atomic THROW click that never involves the cursor. */
    public boolean dropSlot(String owner, int priority, int menuSlot, boolean wholeStack) {
        if (clickedThisTick || owner == null || owner.isBlank()
                || menuSlot < 0 || menuSlot > InventoryTransfers.MENU_OFFHAND) return false;
        if (!controls.ready() || !controls.cursorEmpty() || !canPreempt(owner, priority)) return false;
        finish();
        clickedThisTick = true;
        controls.drop(menuSlot, wholeStack);
        return true;
    }

    /** Starts the click budget and advances a plan. Call once per client tick, before modules. */
    public void tick() {
        clickedThisTick = false;
        if (owner == null) return;
        if (!controls.ready(containerId)) {
            // Drop the untrusted remainder and recover only after the ordinary player menu returns.
            plan = new Click[0];
            step = 0;
            recovering = true;
            recoveryBlocked = true;
            containerId = -1;
            return;
        }
        if (cooldown > 0) { cooldown--; return; }
        if (step < plan.length) {
            Click click = plan[step++];
            clickedThisTick = true;
            controls.pickup(containerId, click.menuSlot(), click.button());
            cooldown = planDelay;
            return;
        }
        if (!controls.cursorEmpty(containerId)) {
            int destination = controls.returnStorageMenuSlot(containerId);
            if (destination >= 0) {
                clickedThisTick = true;
                controls.pickup(containerId, destination, 0);
                cooldown = planDelay;
                recovering = true;
                recoveryBlocked = false;
                return;
            }
            // A full inventory holds the channel rather than dropping a carried item.
            recovering = true;
            recoveryBlocked = true;
            return;
        }
        finish();
    }

    public boolean recovering() { return recovering; }
    public boolean recoveryBlocked() { return recovering && recoveryBlocked; }

    /** Releases ownership unless a cursor stack still needs safe recovery. */
    public void release(String owner) {
        if (!owns(owner)) return;
        if (!controls.ready(containerId) || !controls.cursorEmpty(containerId)) {
            plan = new Click[0];
            step = 0;
            recovering = true;
            recoveryBlocked = !controls.ready(containerId)
                    || (!controls.cursorEmpty(containerId) && controls.returnStorageMenuSlot(containerId) < 0);
            if (!controls.ready(containerId)) containerId = -1;
            return;
        }
        finish();
    }

    /** Unconditional teardown for world changes and player replacement. */
    public void clear() {
        owner = null; priority = 0; plan = new Click[0]; step = 0; containerId = -1;
        cooldown = 0; recovering = false; recoveryBlocked = false; clickedThisTick = false;
    }

    private void finish() {
        owner = null; priority = 0; plan = new Click[0]; step = 0; containerId = -1;
        recovering = false; recoveryBlocked = false;
    }
}
