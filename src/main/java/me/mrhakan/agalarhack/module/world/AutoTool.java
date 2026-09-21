package me.mrhakan.agalarhack.module.world;

import me.mrhakan.agalarhack.services.InventoryService;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Selects the fastest hotbar tool for the block currently being mined. */
public class AutoTool extends Module {
    private static final String OWNER = "autotool";
    private static final int PRIORITY = 40;
    private int candidateSlot = -1;
    private int candidateTicks;

    public AutoTool() {
        super("AutoTool", Category.WORLD, "Automatically selects the fastest hotbar tool while mining");
    }

    @Override
    public void selfSettings() {
        addBooleanSetting("swapBack", true, "Return to the previous hotbar slot after mining");
        addBooleanSetting("miningOnly", true, "Only switch tools while the attack key is held");
        addBooleanSetting("preferCurrent", true, "Keep the selected tool when it is effectively as fast as the best option");
        addNumberSetting("minDurability", 5.0, 0.0, 1000.0, "Avoid damageable tools with this many or fewer uses remaining");
        addNumberSetting("switchThreshold", 0.15, 0.0, 5.0, "Minimum destroy-speed improvement required before switching tools");
        addNumberSetting("switchDelay", 0.0, 0.0, 10.0, "Ticks a better tool must remain preferred before switching; helps prevent hotbar flicker while aiming");
    }

    @Override
    public void onUpdate() {
        InventoryService inventory = service(InventoryService.class);
        if (mc.player == null || mc.level == null || mc.gui.screen() != null || mc.player.isUsingItem()
                || !(mc.hitResult instanceof BlockHitResult hit)
                || (getBooleanSetting("miningOnly", true) && !mc.options.keyAttack.isDown())) {
            inventory.release(OWNER);
            resetCandidate();
            return;
        }
        int minimumDurability = (int) Math.round(getNumberSetting("minDurability", 5));
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        int slot = inventory.findBestTool(state, minimumDurability);
        if (slot < 0) {
            inventory.release(OWNER);
            resetCandidate();
            return;
        }
        int desired = hold(inventory, state, slot, minimumDurability);
        int selected = inventory.selectedSlot();
        if (desired != selected) {
            if (desired != candidateSlot) {
                candidateSlot = desired;
                candidateTicks = 1;
            } else {
                candidateTicks++;
            }
            int delay = (int) Math.round(getNumberSetting("switchDelay", 0.0));
            if (candidateTicks <= delay) return;
        } else {
            resetCandidate();
        }
        if (inventory.select(OWNER, PRIORITY, desired, false, getBooleanSetting("swapBack", true))) {
            resetCandidate();
        }
    }
    /**
     * The slot to actually hold: the best one, unless the slot already selected is close enough.
     *
     * <p>Hysteresis ported from main's fd2d295. Two tools within a hair of each other made the
     * module swap every tick for a destroy speed the player cannot feel, and each swap is a visible
     * hand animation. The {@code +1000} correct-tool bonus dwarfs any threshold the slider allows,
     * so this only ever holds a tool of the same correctness class - reaching for the right tool is
     * never suppressed.
     */
    private int hold(InventoryService inventory, BlockState state, int best, int minimumDurability) {
        if (!getBooleanSetting("preferCurrent", true) || mc.player == null) return best;
        int current = mc.player.getInventory().getSelectedSlot();
        if (current == best) return best;
        double currentScore = inventory.toolScore(current, state, minimumDurability);
        // A negative score is a refusal - an empty hand or a tool at its durability floor - and is
        // never worth holding on to, however small the difference looks.
        if (currentScore < 0) return best;
        double improvement = inventory.toolScore(best, state, minimumDurability) - currentScore;
        return improvement <= getNumberSetting("switchThreshold", 0.15) ? current : best;
    }

    private void resetCandidate() {
        candidateSlot = -1;
        candidateTicks = 0;
    }

    @Override public void onDisable() {
        service(InventoryService.class).release(OWNER);
        resetCandidate();
    }
    @Override public void onDisconnect() { onDisable(); }
}
