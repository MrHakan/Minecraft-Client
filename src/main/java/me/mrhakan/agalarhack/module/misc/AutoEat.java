package me.mrhakan.agalarhack.module.misc;

import me.mrhakan.agalarhack.services.InventoryService;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

/** Automatically eats the best allowed food from the hotbar. */
public class AutoEat extends Module {
    private static final String OWNER = "autoeat";
    private static final int PRIORITY = 60;

    public AutoEat() {
        super("AutoEat", Category.PLAYER, "Automatically eats suitable food from the hotbar when hunger is low");
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
        InventoryService inventory = service(InventoryService.class);
        if (mc.player == null || mc.gui.screen() != null) { inventory.release(OWNER); return; }
        int hunger = mc.player.getFoodData().getFoodLevel();
        int threshold = (int) Math.round(getNumberSetting("hunger", 12));
        boolean eating = inventory.owns(OWNER);
        boolean hungry = eating && getBooleanSetting("fillToFull", true)
                ? mc.player.getFoodData().needsFood() : hunger <= threshold;
        if (!hungry || (!eating && mc.player.isUsingItem())) { inventory.release(OWNER); return; }
        boolean allowGolden = getBooleanSetting("allowGoldenApples", false);
        // main's avoidWaste: rank on the nutrition the bar can still take, so a full-value meal is
        // not spent on a bar that is one point down.
        int missingHunger = getBooleanSetting("avoidWaste", true)
                ? Math.max(1, 20 - hunger) : InventoryService.IGNORE_WASTE;
        int slot = inventory.findFood(allowGolden, true, missingHunger);
        if (slot < 0 || !inventory.select(OWNER, PRIORITY, hold(inventory, slot, allowGolden, missingHunger),
                true, getBooleanSetting("swapBack", true))) {
            inventory.release(OWNER);
        }
    }

    /**
     * The slot to actually eat from: the one already selected, when its food is close enough to the
     * best that swapping is not worth the hand animation.
     *
     * <p>Ported from main's hysteresis and its later avoidWaste refinement (0c5801e) rather than
     * copied, because those versions reached for the hotbar directly and this branch goes through
     * the lease. Ranking is by {@link InventoryService#foodValue}, the rule {@code findFood} itself
     * selects on.
     */
    private int hold(InventoryService inventory, int best, boolean allowGolden, int missingHunger) {
        if (!getBooleanSetting("preferCurrent", true)) return best;
        int current = inventory.selectedSlot();
        if (current == best) return best;
        // A refusal - an empty hand, a non-food, or a golden apple while those are off - is never held.
        InventoryService.FoodValue selected = inventory.foodValue(current, allowGolden, missingHunger);
        if (selected.refused()) return best;
        InventoryService.FoodValue top = inventory.foodValue(best, allowGolden, missingHunger);
        int tolerance = (int) Math.round(getNumberSetting("nutritionTolerance", 2.0));
        // Both of main's conditions: close enough on what the bar would actually take, and not
        // throwing away materially more of it - so "keep what you are holding" never becomes
        // eating a cake to fill half a point.
        boolean closeEnough = top.useful() - selected.useful() <= tolerance;
        boolean noWorseWaste = missingHunger == InventoryService.IGNORE_WASTE
                || selected.waste() <= top.waste() + tolerance;
        return closeEnough && noWorseWaste ? current : best;
    }
    @Override public void onDisable() { service(InventoryService.class).release(OWNER); }
    @Override public void onDisconnect() { onDisable(); }
}
