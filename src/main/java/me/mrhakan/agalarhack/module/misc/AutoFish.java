package me.mrhakan.agalarhack.module.misc;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.BobberBite;
import me.mrhakan.agalarhack.services.InventoryService;
import net.minecraft.world.item.Items;

/**
 * Reels in when the bobber is pulled under, and casts again.
 *
 * <p>It does not know there is a fish. {@code FishingHook}'s nibble counter and state machine are
 * server-side and private, and the client's copy of the entity carries neither, so this watches the
 * bobber being pulled under — the same cue a player watches for. That is why the settings talk about
 * the bobber's motion rather than about a catch.
 *
 * <p>Use is pulsed for a single tick rather than held, so one bite produces one right-click and one
 * recast another. At most one use is ever pending, and whichever action it was for is cancelled if
 * the world stops agreeing with it — a hook appearing cancels a pending cast, a hook vanishing
 * cancels a pending reel. Without that, a slow server produces two casts for one bite.
 */
public class AutoFish extends Module {
    private static final String OWNER = "autofish";
    /** Below AutoEat: being fed matters more than a fish, and the two want the same hand. */
    private static final int PRIORITY = 40;

    private BobberBite.Detector detector;
    private long tick;
    /** Ticks until the pending use fires. */
    private int waiting;
    private boolean pendingCast;
    private boolean pendingReel;

    public AutoFish() {
        super("AutoFish", Category.MISC, "Reels in when the bobber is pulled under, then casts again");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("pullThreshold", BobberBite.DEFAULT_THRESHOLD * 100, 1.0, 40.0,
                "How hard the bobber must be pulled down to count, in hundredths of a block per tick");
        addNumberSetting("reelDelay", 4.0, 0.0, 40.0, "Ticks to wait after the pull before reeling in");
        addNumberSetting("castDelay", 12.0, 2.0, 100.0, "Ticks to wait after reeling in before casting again");
        addBooleanSetting("autoCast", true, "Cast again on its own; with this off it only reels in");
        addBooleanSetting("swapBack", true, "Return to the previous hotbar slot when it stops");
    }

    @Override
    public void onEnable() {
        rebuild();
        tick = 0;
        clearPending();
    }

    @Override
    public void onDisable() {
        service(InventoryService.class).release(OWNER);
        clearPending();
    }

    @Override public void onDisconnect() { onDisable(); }

    @Override
    public void onWorldChanged(boolean ready) {
        onDisable();
        super.onWorldChanged(ready);
    }

    private void clearPending() {
        if (detector != null) detector.reset();
        waiting = 0;
        pendingCast = false;
        pendingReel = false;
    }

    private void rebuild() {
        // A plunge lasts several ticks, so one bite must not be recognised repeatedly. The gap is
        // tied to the recast wait so it always outlasts the plunge the module is responding to.
        int gap = Math.max(10, (int) Math.round(getNumberSetting("castDelay", 12.0)));
        detector = new BobberBite.Detector(
                getNumberSetting("pullThreshold", BobberBite.DEFAULT_THRESHOLD * 100) / 100.0, gap);
    }

    @Override
    public void onUpdate() {
        InventoryService inventory = service(InventoryService.class);
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) { stand(inventory); return; }
        if (detector == null) rebuild();

        int rod = inventory.findHotbar(stack -> stack.is(Items.FISHING_ROD));
        if (rod < 0) { stand(inventory); return; }
        tick++;

        var hook = mc.player.fishing;
        boolean cast = hook != null && hook.isAlive();

        // The world disagreeing with a pending action cancels it rather than letting it fire late.
        if (cast && pendingCast) { pendingCast = false; waiting = 0; }
        if (!cast && pendingReel) { pendingReel = false; waiting = 0; }

        if (pendingCast || pendingReel) {
            if (waiting > 0) { waiting--; hold(inventory, rod, false); return; }
            pendingCast = false;
            pendingReel = false;
            hold(inventory, rod, true);
            return;
        }

        if (!cast) {
            detector.reset();
            if (getBooleanSetting("autoCast", true)) {
                pendingCast = true;
                waiting = (int) Math.round(getNumberSetting("castDelay", 12.0));
            }
            hold(inventory, rod, false);
            return;
        }

        if (detector.update(tick, hook.isInWater(), hook.getDeltaMovement().y)) {
            pendingReel = true;
            waiting = (int) Math.round(getNumberSetting("reelDelay", 4.0));
        }
        hold(inventory, rod, false);
    }

    /**
     * Holds the rod slot, pressing use only on the tick a pulse is wanted.
     *
     * <p>The lease presses and releases the key for us, so asking for {@code use} on exactly one tick
     * is what turns a held key into a single right-click.
     */
    private void hold(InventoryService inventory, int rod, boolean pulse) {
        if (!inventory.select(OWNER, PRIORITY, rod, pulse, getBooleanSetting("swapBack", true))) {
            inventory.release(OWNER);
        }
    }

    private void stand(InventoryService inventory) {
        inventory.release(OWNER);
        clearPending();
    }
}
