package me.mrhakan.agalarhack.module.world;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Selects the fastest hotbar tool for the block currently being mined. */
public class AutoTool extends Module {
    private int previousSlot = -1;
    private boolean swapped;

    public AutoTool() {
        super("AutoTool", Category.WORLD, "Automatically selects the fastest hotbar tool while mining");
    }

    @Override
    public void selfSettings() {
        addBooleanSetting("swapBack", true, "Return to the previous hotbar slot after mining");
        addBooleanSetting("miningOnly", true, "Only switch tools while the attack key is held");
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null) {
            return;
        }

        boolean mining = mc.options.keyAttack.isDown();
        if (!(mc.hitResult instanceof BlockHitResult hit)
                || (getBooleanSetting("miningOnly", true) && !mining)) {
            restoreSlot();
            return;
        }

        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        int bestSlot = findBestSlot(state);
        int current = mc.player.getInventory().getSelectedSlot();
        if (bestSlot < 0 || bestSlot == current) {
            if (!mining) {
                restoreSlot();
            }
            return;
        }

        if (!swapped && getBooleanSetting("swapBack", true)) {
            previousSlot = current;
        }
        mc.player.getInventory().setSelectedSlot(bestSlot);
        swapped = true;
    }

    private int findBestSlot(BlockState state) {
        int best = -1;
        float bestSpeed = 1.0f;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = slot;
            }
        }
        return best;
    }

    private void restoreSlot() {
        if (swapped && getBooleanSetting("swapBack", true) && previousSlot >= 0 && previousSlot < 9
                && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
        swapped = false;
    }

    @Override
    public void onDisable() {
        restoreSlot();
    }
}
