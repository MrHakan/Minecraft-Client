package me.mrhakan.agalarhack.module.misc;

import me.mrhakan.agalarhack.services.InventoryService;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

/** Automatically eats the best allowed food from the hotbar. */
public class AutoEat extends Module {
    private static final String OWNER = "autoeat";
    private static final int PRIORITY = 60;

    public AutoEat() {
        super("AutoEat", Category.PLAYER, "Automatically eats food from the hotbar when hunger is low");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("hunger", 12.0, 1.0, 20.0, "Start eating at or below this hunger level");
        addBooleanSetting("fillToFull", true, "Continue eating until the hunger bar is full");
        addBooleanSetting("avoidWaste", true, "Prefer food that fills the missing hunger without wasting nutrition");
        addBooleanSetting("swapBack", true, "Return to the previous hotbar slot afterwards");
        addBooleanSetting("preferCurrent", true, "Keep the selected food when it is close to the best option to avoid needless hotbar swaps");
        addNumberSetting("nutritionTolerance", 2.0, 0.0, 10.0, "Maximum nutrition points the selected food may trail the best food by");
        addBooleanSetting("allowGoldenApples", false, "Allow automatic use of golden/enchanted golden apples");
    }

    @Override
    public void onUpdate() {
        InventoryService inventory = service(InventoryService.class);
        if (mc.player == null || mc.gui.screen() != null) { inventory.release(OWNER); return; }
        int hunger = mc.player.getFoodData().getFoodLevel();
        int threshold = (int) Math.round(getNumberSetting("hunger", 12));
        boolean eating = inventory.owns(OWNER);
        boolean hungry = eating && getBooleanSetting("fillToFull", true)
                ? mc.player.getFoodData().needsFood() : hunger <= threshold;
        if (!hungry || (!eating && mc.player.isUsingItem())) { inventory.release(OWNER); return; }
        boolean allowGolden = getBooleanSetting("allowGoldenApples", false);
        boolean avoidWaste = getBooleanSetting("avoidWaste", true);
        int missingHunger = Math.max(1, 20 - hunger);
        int slot = inventory.findFood(allowGolden, true, missingHunger, avoidWaste);
        if (slot < 0 || !inventory.select(OWNER, PRIORITY,
                hold(inventory, slot, allowGolden, missingHunger, avoidWaste),
                true, getBooleanSetting("swapBack", true))) {
            inventory.release(OWNER);
        }
    }

    /**
     * The slot to actually eat from: the one already selected, when its food is close enough to the
     * best that swapping is not worth the hand animation.
     *
     * <p>Ported from main's own hysteresis rather than copied, because that version reached for the
     * hotbar directly and this branch goes through the lease. Ranking is by
     * {@link InventoryService#foodScore}, the rule {@code findFood} itself selects on.
     */
    private int hold(InventoryService inventory, int best, boolean allowGolden,
            int missingHunger, boolean avoidWaste) {
        if (!getBooleanSetting("preferCurrent", true)) return best;
        int current = inventory.selectedSlot();
        if (current == best) return best;
        double currentNutrition = inventory.foodScore(current, allowGolden);
        double bestNutrition = inventory.foodScore(best, allowGolden);
        if (currentNutrition < 0 || bestNutrition < 0) return best;

        double tolerance = Math.round(getNumberSetting("nutritionTolerance", 2.0));
        double currentUseful = avoidWaste ? Math.min(currentNutrition, missingHunger) : currentNutrition;
        double bestUseful = avoidWaste ? Math.min(bestNutrition, missingHunger) : bestNutrition;
        double currentWaste = avoidWaste ? Math.max(0, currentNutrition - missingHunger) : 0;
        double bestWaste = avoidWaste ? Math.max(0, bestNutrition - missingHunger) : 0;
        return bestUseful - currentUseful <= tolerance
                && (!avoidWaste || currentWaste <= bestWaste + tolerance) ? current : best;
    }
    @Override public void onDisable() { service(InventoryService.class).release(OWNER); }
    @Override public void onDisconnect() { onDisable(); }
}
