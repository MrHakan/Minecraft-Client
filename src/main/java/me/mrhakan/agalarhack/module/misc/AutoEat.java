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
    private int appliedSlot = -1;

    public AutoEat() {
        super("AutoEat", Category.MISC, "Automatically eats suitable food from the hotbar when hunger is low");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("hunger", 12.0, 1.0, 20.0, "Start eating at or below this hunger level");
        addBooleanSetting("fillToFull", true, "Continue eating until the hunger bar is full");
        addBooleanSetting("avoidWaste", true, "Prefer food that fills the missing hunger without wasting nutrition");
        addBooleanSetting("swapBack", true, "Return to the previous hotbar slot afterwards");
        addBooleanSetting("preferCurrent", true, "Keep the selected food when it is close to the best option to avoid needless hotbar swaps");
        addNumberSetting("nutritionTolerance", 2.0, 0.0, 10.0, "Maximum useful nutrition points the selected food may trail the best food by");
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

        if (hunger > threshold || mc.player.isUsingItem()) return;

        int bestSlot = findBestFoodSlot(hunger);
        if (bestSlot < 0 || !claimControls()) return;

        int selected = mc.player.getInventory().getSelectedSlot();
        if (getBooleanSetting("swapBack", true)) previousSlot = selected;
        if (selected != bestSlot) mc.player.getInventory().setSelectedSlot(bestSlot);
        appliedSlot = bestSlot;

        ownsUseKey = !mc.options.keyUse.isDown();
        mc.options.keyUse.setDown(true);
        eating = true;
    }

    private int findBestFoodSlot(int hunger) {
        int bestSlot = -1;
        int bestUsefulNutrition = -1;
        int bestWaste = Integer.MAX_VALUE;
        int selectedUsefulNutrition = -1;
        int selectedWaste = Integer.MAX_VALUE;
        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        int missingHunger = Math.max(1, 20 - hunger);
        boolean allowGolden = getBooleanSetting("allowGoldenApples", false);
        boolean avoidWaste = getBooleanSetting("avoidWaste", true);
        boolean preferCurrent = getBooleanSetting("preferCurrent", true);
        int tolerance = preferCurrent ? (int) Math.round(getNumberSetting("nutritionTolerance", 2.0)) : 0;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (!allowGolden && (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE)) continue;

            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null) continue;
            int nutrition = food.nutrition();
            int usefulNutrition = avoidWaste ? Math.min(nutrition, missingHunger) : nutrition;
            int waste = avoidWaste ? Math.max(0, nutrition - missingHunger) : 0;

            if (slot == selectedSlot) {
                selectedUsefulNutrition = usefulNutrition;
                selectedWaste = waste;
            }
            if (usefulNutrition > bestUsefulNutrition
                    || (usefulNutrition == bestUsefulNutrition && waste < bestWaste)) {
                bestUsefulNutrition = usefulNutrition;
                bestWaste = waste;
                bestSlot = slot;
            }
        }

        if (preferCurrent && selectedUsefulNutrition >= 0
                && bestUsefulNutrition - selectedUsefulNutrition <= tolerance
                && (!avoidWaste || selectedWaste <= bestWaste + tolerance)) {
            return selectedSlot;
        }
        return bestSlot;
    }

    private boolean claimControls() {
        boolean hotbar = AgalarHackClient.UTILITY_ACTIONS.claimHotbar(OWNER, PRIORITY);
        boolean use = AgalarHackClient.UTILITY_ACTIONS.claimUse(OWNER, PRIORITY);
        return hotbar && use;
    }

    private void stopEating() {
        if (ownsUseKey) mc.options.keyUse.setDown(false);
        if (eating && getBooleanSetting("swapBack", true) && previousSlot >= 0 && previousSlot < 9
                && mc.player != null && mc.player.getInventory().getSelectedSlot() == appliedSlot) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        eating = false;
        ownsUseKey = false;
        previousSlot = -1;
        appliedSlot = -1;
    }

    @Override
    public void onDisable() {
        stopEating();
    }
}
