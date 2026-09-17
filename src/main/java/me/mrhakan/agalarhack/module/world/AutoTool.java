package me.mrhakan.agalarhack.module.world;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Selects the fastest hotbar tool for the block currently being mined. */
public class AutoTool extends Module {
    private static final String OWNER = "autotool";
    private static final int PRIORITY = 40;

    private int previousSlot = -1;
    private int appliedSlot = -1;
    private boolean swapped;

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
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) {
            restoreSlot();
            return;
        }

        boolean mining = mc.options.keyAttack.isDown();
        if (!(mc.hitResult instanceof BlockHitResult hit)
                || (getBooleanSetting("miningOnly", true) && !mining)) {
            restoreSlot();
            return;
        }

        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        int current = mc.player.getInventory().getSelectedSlot();
        int bestSlot = findBestSlot(state, current);
        if (bestSlot < 0 || bestSlot == current) {
            if (!mining) {
                restoreSlot();
            }
            return;
        }
        if (!AgalarHackClient.UTILITY_ACTIONS.claimHotbar(OWNER, PRIORITY)) {
            return;
        }

        if (!swapped && getBooleanSetting("swapBack", true)) {
            previousSlot = current;
        }
        mc.player.getInventory().setSelectedSlot(bestSlot);
        appliedSlot = bestSlot;
        swapped = true;
    }

    private int findBestSlot(BlockState state, int currentSlot) {
        int best = -1;
        double bestScore = 1.0;
        double currentScore = score(mc.player.getInventory().getItem(currentSlot), state);
        int minimumDurability = (int) Math.round(getNumberSetting("minDurability", 5.0));
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!isUsable(stack, minimumDurability)) {
                continue;
            }
            double score = score(stack, state);
            if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        if (best < 0 || !getBooleanSetting("preferCurrent", true) || !isUsable(mc.player.getInventory().getItem(currentSlot), minimumDurability)) {
            return best;
        }
        double threshold = getNumberSetting("switchThreshold", 0.15);
        return bestScore - currentScore <= threshold ? currentSlot : best;
    }

    private boolean isUsable(ItemStack stack, int minimumDurability) {
        return !stack.isEmpty()
                && (!stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > minimumDurability);
    }

    private double score(ItemStack stack, BlockState state) {
        if (stack.isEmpty()) {
            return 0.0;
        }
        return stack.getDestroySpeed(state) + (stack.isCorrectToolForDrops(state) ? 1000.0 : 0.0);
    }

    private void restoreSlot() {
        if (swapped && getBooleanSetting("swapBack", true) && previousSlot >= 0 && previousSlot < 9
                && mc.player != null && mc.player.getInventory().getSelectedSlot() == appliedSlot) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
        appliedSlot = -1;
        swapped = false;
    }

    @Override
    public void onDisable() {
        restoreSlot();
    }
}
