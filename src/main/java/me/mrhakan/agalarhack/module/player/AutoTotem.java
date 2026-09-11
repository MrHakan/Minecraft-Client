package me.mrhakan.agalarhack.module.player;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.InventoryService;
import me.mrhakan.agalarhack.services.InventoryTransfers;
import me.mrhakan.agalarhack.services.NotificationService;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Keeps a totem of undying in the offhand and can hand the slot back when the danger passes. */
public class AutoTotem extends Module {
    private static final String OWNER = "autototem";
    /** Above AutoArmor: staying alive outranks wearing a slightly better helmet. */
    private static final int PRIORITY = 90;

    private int cooldown;
    private int restoreIndex = -1;
    private Item restoreItem;
    private int lastReportedCount = -1;

    public AutoTotem() {
        super("AutoTotem", Category.PLAYER, "Keeps a totem of undying in the offhand and reports how many are left");
        markExperimental();
    }

    @Override
    public void selfSettings() {
        addChoiceSetting("mode", "health", "Always hold a totem, or only once health drops", "always", "health");
        addNumberSetting("health", 12, 1, 40, "Equip at or below this combined health and absorption");
        addNumberSetting("restoreMargin", 6, 0, 20, "Extra health above the threshold before the offhand is handed back");
        addBooleanSetting("restore", true, "Return the previous offhand item once health has recovered");
        addNumberSetting("delay", 2, 0, 40, "Ticks to wait between offhand swaps");
        addBooleanSetting("warnWhenEmpty", true, "Notify once when no totem is left in the inventory");
    }

    @Override
    public void onEnable() { reset(); }

    @Override
    public void onDisable() {
        reset();
        service(InventoryService.class).transfers().release(OWNER);
        setDisplayName(null);
    }

    @Override public void onDisconnect() { onDisable(); }

    private void reset() {
        cooldown = 0;
        restoreIndex = -1;
        restoreItem = null;
        lastReportedCount = -1;
    }

    @Override
    public void onUpdate() {
        InventoryService inventory = service(InventoryService.class);
        var transfers = inventory.transfers();
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) { setDisplayName(null); return; }

        int available = countTotems();
        boolean holding = mc.player.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.TOTEM_OF_UNDYING);
        setDisplayName("AutoTotem [" + (available + (holding ? 1 : 0)) + "]");
        warnIfEmpty(available, holding);

        // Deliberately not gated on transfers.busy(): AutoTotem outranks AutoArmor and is allowed to
        // take the channel from it. The controller rejects self-preemption, so an in-flight totem
        // swap still completes untouched.
        if (cooldown > 0) { cooldown--; return; }

        boolean wanted = wantsTotem();
        if (holding) {
            if (!wanted && recovered()) tryRestore(inventory);
            return;
        }
        // A totem that leaves the offhand without us moving it was consumed or taken by the player.
        restoreIndex = -1;
        restoreItem = null;
        if (!wanted || available <= 0) return;

        int source = inventory.findItem(Items.TOTEM_OF_UNDYING, false);
        if (source < 0) return;
        equip(inventory, source);
    }

    private void equip(InventoryService inventory, int source) {
        var transfers = inventory.transfers();
        ItemStack displaced = mc.player.getItemBySlot(EquipmentSlot.OFFHAND);
        Item displacedItem = displaced.isEmpty() ? null : displaced.getItem();
        boolean started;
        if (InventoryTransfers.isHotbarIndex(source)) {
            // A single atomic swap; the displaced item lands in the totem's old hotbar slot.
            started = transfers.swapHotbar(OWNER, PRIORITY, InventoryTransfers.MENU_OFFHAND, source);
        } else {
            started = transfers.begin(OWNER, PRIORITY,
                    InventoryTransfers.equipPlan(source, InventoryTransfers.MENU_OFFHAND));
        }
        if (!started) return;
        cooldown = (int) Math.round(getNumberSetting("delay", 2));
        restoreIndex = displacedItem == null ? -1 : source;
        restoreItem = displacedItem;
    }

    /** Only undoes a swap this module made, and only if the item is still where it was put. */
    private void tryRestore(InventoryService inventory) {
        if (!getBooleanSetting("restore", true) || restoreIndex < 0 || restoreItem == null) return;
        if (!inventory.stackAt(restoreIndex).is(restoreItem)) { restoreIndex = -1; restoreItem = null; return; }
        var transfers = inventory.transfers();
        boolean started = InventoryTransfers.isHotbarIndex(restoreIndex)
                ? transfers.swapHotbar(OWNER, PRIORITY, InventoryTransfers.MENU_OFFHAND, restoreIndex)
                : transfers.begin(OWNER, PRIORITY,
                        InventoryTransfers.equipPlan(restoreIndex, InventoryTransfers.MENU_OFFHAND));
        if (!started) return;
        cooldown = (int) Math.round(getNumberSetting("delay", 2));
        restoreIndex = -1;
        restoreItem = null;
    }

    private boolean wantsTotem() {
        if (!"health".equalsIgnoreCase(getStringSetting("mode", "health"))) return true;
        return health() <= getNumberSetting("health", 12);
    }

    /** Hysteresis keeps the offhand from flickering while health hovers around the threshold. */
    private boolean recovered() {
        return health() >= getNumberSetting("health", 12) + getNumberSetting("restoreMargin", 6);
    }

    private double health() {
        return mc.player.getHealth() + mc.player.getAbsorptionAmount();
    }

    private int countTotems() {
        int count = 0;
        for (int slot = 0; slot < InventoryTransfers.INVENTORY_SIZE; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.is(Items.TOTEM_OF_UNDYING)) count += stack.getCount();
        }
        return count;
    }

    private void warnIfEmpty(int available, boolean holding) {
        if (!getBooleanSetting("warnWhenEmpty", true)) return;
        int total = available + (holding ? 1 : 0);
        if (total == lastReportedCount) return;
        if (total == 0 && lastReportedCount > 0) {
            service(NotificationService.class).publish(NotificationService.Type.WARNING, "No totems left");
        }
        lastReportedCount = total;
    }
}
