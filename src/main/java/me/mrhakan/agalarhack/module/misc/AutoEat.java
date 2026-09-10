package me.mrhakan.agalarhack.module.misc;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Automatically eats the best allowed food from the hotbar. */
public class AutoEat extends Module {
    private static final String OWNER = "autoeat";
    private static final int PRIORITY = 60;

    private boolean eating;
    private boolean ownsUseKey;
    private int previousSlot = -1;

    public AutoEat() {
        super("AutoEat", Category.MISC, "Automatically eats food from the hotbar when hunger is low");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("hunger", 12.0, 1.0, 20.0, "Start eating at or below this hunger level");
        addBooleanSetting("fillToFull", true, "Continue eating until the hunger bar is full");
        addBooleanSetting("swapBack", true, "Return to the previous hotbar slot afterwards");
        addBooleanSetting("allowGoldenApples", false, "Allow automatic use of golden/enchanted golden apples");
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.gui.screen() != null) {
            stopEating();
            return;
        }

        int hunger = mc.player.getFoodData().getFoodLevel();
        int threshold = (int) Math.round(getNumberSetting("hunger", 12.0));

        if (eating) {
            boolean shouldContinue = getBooleanSetting("fillToFull", true)
                    ? mc.player.getFoodData().needsFood()
                    : hunger <= threshold;
            if (!shouldContinue) {
                stopEating();
                return;
            }
            if (!claimControls()) {
                stopEating();
                return;
            }
            mc.options.keyUse.setDown(true);
            return;
        }

        if (hunger > threshold || mc.player.isUsingItem()) {
            return;
        }

        int bestSlot = findBestFoodSlot();
        if (bestSlot < 0 || !claimControls()) {
            return;
        }

        int selected = mc.player.getInventory().getSelectedSlot();
        if (getBooleanSetting("swapBack", true)) {
            previousSlot = selected;
        }
        if (selected != bestSlot) {
            mc.player.getInventory().setSelectedSlot(bestSlot);
        }

        ownsUseKey = !mc.options.keyUse.isDown();
        mc.options.keyUse.setDown(true);
        eating = true;
    }

    private boolean claimControls() {
        boolean hotbar = AgalarHackClient.UTILITY_ACTIONS.claimHotbar(OWNER, PRIORITY);
        boolean use = AgalarHackClient.UTILITY_ACTIONS.claimUse(OWNER, PRIORITY);
        return hotbar && use;
    }

    private int findBestFoodSlot() {
        int bestSlot = -1;
        int bestNutrition = -1;
        boolean allowGolden = getBooleanSetting("allowGoldenApples", false);

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            if (!allowGolden && (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE)) {
                continue;
            }
            FoodProperties food = item.components().get(DataComponents.FOOD);
            if (food == null) {
                continue;
            }
            if (food.nutrition() > bestNutrition) {
                bestNutrition = food.nutrition();
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private void stopEating() {
        if (ownsUseKey) {
            mc.options.keyUse.setDown(false);
        }
        if (eating && getBooleanSetting("swapBack", true) && previousSlot >= 0 && previousSlot < 9
                && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        eating = false;
        ownsUseKey = false;
        previousSlot = -1;
    }

    @Override
    public void onDisable() {
        stopEating();
    }
}
