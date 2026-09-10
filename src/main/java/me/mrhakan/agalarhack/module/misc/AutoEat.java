package me.mrhakan.agalarhack.module.misc;

import me.mrhakan.agalarhack.services.InventoryService;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

/** Automatically eats the best allowed food from the hotbar. */
public class AutoEat extends Module {
    private static final String OWNER = "autoeat";
    private static final int PRIORITY = 60;

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
        InventoryService inventory = service(InventoryService.class);
        if (mc.player == null || mc.gui.screen() != null) { inventory.release(OWNER); return; }
        int hunger = mc.player.getFoodData().getFoodLevel();
        int threshold = (int) Math.round(getNumberSetting("hunger", 12));
        boolean eating = inventory.owns(OWNER);
        boolean hungry = eating && getBooleanSetting("fillToFull", true)
                ? mc.player.getFoodData().needsFood() : hunger <= threshold;
        if (!hungry || (!eating && mc.player.isUsingItem())) { inventory.release(OWNER); return; }
        int slot = inventory.findFood(getBooleanSetting("allowGoldenApples", false));
        if (slot < 0 || !inventory.select(OWNER, PRIORITY, slot, true, getBooleanSetting("swapBack", true))) {
            inventory.release(OWNER);
        }
    }
    @Override public void onDisable() { service(InventoryService.class).release(OWNER); }
    @Override public void onDisconnect() { onDisable(); }
}
