package me.mrhakan.agalarhack.module.player;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.InventoryService;
import me.mrhakan.agalarhack.services.InventoryTransfers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/** Tops up running-low hotbar stacks from the rest of the inventory. */
public class AutoRefill extends Module {
    private static final String OWNER = "autorefill";
    /** Below AutoTotem and AutoArmor: a refill can always wait a tick longer. */
    private static final int PRIORITY = 30;

    private int cooldown;

    public AutoRefill() {
        super("AutoRefill", Category.PLAYER, "Refills low hotbar stacks from the inventory before they run out");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("threshold", 8, 1, 64, "Refill a hotbar stack at or below this many items");
        addNumberSetting("delay", 4, 0, 40, "Ticks to wait between refills");
        addNumberSetting("clickDelay", 1, 0, 10, "Ticks between the individual clicks of one refill");
        addBooleanSetting("selectedOnly", false, "Only refill the slot currently held");
        addBooleanSetting("preserveNamed", true, "Never move renamed or custom items");
    }

    @Override public void onEnable() { cooldown = 0; }

    @Override
    public void onDisable() {
        cooldown = 0;
        service(InventoryService.class).transfers().release(OWNER);
    }

    @Override public void onDisconnect() { onDisable(); }

    @Override
    public void onUpdate() {
        InventoryService inventory = service(InventoryService.class);
        var transfers = inventory.transfers();
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) return;
        if (transfers.busy()) return;
        if (cooldown > 0) { cooldown--; return; }

        int clickDelay = (int) Math.round(getNumberSetting("clickDelay", 1));
        int threshold = (int) Math.round(getNumberSetting("threshold", 8));
        boolean preserveNamed = getBooleanSetting("preserveNamed", true);
        boolean selectedOnly = getBooleanSetting("selectedOnly", false);
        int selected = inventory.selectedSlot();

        for (int hotbar = 0; hotbar < InventoryTransfers.HOTBAR_SIZE; hotbar++) {
            if (selectedOnly && hotbar != selected) continue;
            ItemStack target = mc.player.getInventory().getItem(hotbar);
            if (!needsRefill(target, threshold, preserveNamed)) continue;
            int source = findSource(target, preserveNamed);
            if (source < 0) continue;
            // Clicking a slot that already holds the same item merges into it, so the standard
            // three-click plan tops the hotbar stack up and returns any remainder to the source.
            if (transfers.begin(OWNER, PRIORITY, InventoryTransfers.equipPlan(source, InventoryTransfers.menuSlot(hotbar)), clickDelay)) {
                cooldown = (int) Math.round(getNumberSetting("delay", 4));
            }
            return;
        }
    }

    private boolean needsRefill(ItemStack stack, int threshold, boolean preserveNamed) {
        if (stack.isEmpty() || !stack.isStackable()) return false;
        if (preserveNamed && stack.has(DataComponents.CUSTOM_NAME)) return false;
        int max = maxStackSize(stack);
        // A stack that is already full, or one whose maximum is below the threshold, is not "low".
        return stack.getCount() < max && stack.getCount() <= Math.min(threshold, max - 1);
    }

    /** Prefers the smallest matching stack so the inventory consolidates instead of fragmenting. */
    private int findSource(ItemStack target, boolean preserveNamed) {
        int best = -1;
        int bestCount = Integer.MAX_VALUE;
        for (int slot = InventoryTransfers.HOTBAR_SIZE; slot < InventoryTransfers.INVENTORY_SIZE; slot++) {
            ItemStack candidate = mc.player.getInventory().getItem(slot);
            if (candidate.isEmpty() || !ItemStack.isSameItemSameComponents(candidate, target)) continue;
            if (preserveNamed && candidate.has(DataComponents.CUSTOM_NAME)) continue;
            if (candidate.getCount() < bestCount) { bestCount = candidate.getCount(); best = slot; }
        }
        return best;
    }

    static int maxStackSize(ItemStack stack) {
        return stack.getOrDefault(DataComponents.MAX_STACK_SIZE, stack.getItem().getDefaultMaxStackSize());
    }
}
