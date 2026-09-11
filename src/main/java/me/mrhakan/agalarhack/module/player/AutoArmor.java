package me.mrhakan.agalarhack.module.player;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.InventoryService;
import me.mrhakan.agalarhack.services.InventoryTransfers;
import net.minecraft.world.entity.EquipmentSlot;

/** Wears the strongest armour available, one piece at a time, through the shared transfer channel. */
public class AutoArmor extends Module {
    private static final String OWNER = "autoarmor";
    private static final int PRIORITY = 50;
    /** Head to feet, matching the vanilla screen order. */
    private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private int cooldown;

    public AutoArmor() {
        super("AutoArmor", Category.PLAYER, "Equips the best available armour using the shared inventory transfer channel");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("delay", 4, 0, 40, "Ticks to wait between armour swaps");
        addNumberSetting("clickDelay", 1, 0, 10, "Ticks between the individual clicks of one swap");
        addNumberSetting("minImprovement", 0.5, 0, 20, "Score a candidate must beat the worn piece by before swapping");
        addBooleanSetting("storageOnly", false, "Only pull armour from the upper inventory, never from the hotbar");
        addBooleanSetting("preserveNamed", true, "Never automatically equip renamed or custom items");
    }

    @Override
    public void onEnable() { cooldown = 0; }

    @Override
    public void onDisable() {
        cooldown = 0;
        InventoryService inventory = service(InventoryService.class);
        inventory.transfers().release(OWNER);
    }

    @Override public void onDisconnect() { onDisable(); }

    @Override
    public void onUpdate() {
        InventoryService inventory = service(InventoryService.class);
        var transfers = inventory.transfers();
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) return;
        // AutoArmor holds the lowest container priority, so it never tries to take the channel from
        // another module; it simply waits. This also keeps its per-slot candidate scan off the hot path.
        if (transfers.busy()) return;
        if (cooldown > 0) { cooldown--; return; }

        transfers.setDelay((int) Math.round(getNumberSetting("clickDelay", 1)));
        double minimum = getNumberSetting("minImprovement", 0.5);
        boolean preserveNamed = getBooleanSetting("preserveNamed", true);
        boolean storageOnly = getBooleanSetting("storageOnly", false);

        for (EquipmentSlot slot : SLOTS) {
            int source = inventory.findBestArmor(slot, minimum, preserveNamed, storageOnly);
            if (source < 0) continue;
            int target = armorMenuSlot(slot);
            if (target < 0) continue;
            if (transfers.begin(OWNER, PRIORITY, InventoryTransfers.equipPlan(source, target))) {
                cooldown = (int) Math.round(getNumberSetting("delay", 4));
            }
            return;
        }
    }

    private static int armorMenuSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> InventoryTransfers.ArmorPiece.HEAD.menuSlot();
            case CHEST -> InventoryTransfers.ArmorPiece.CHEST.menuSlot();
            case LEGS -> InventoryTransfers.ArmorPiece.LEGS.menuSlot();
            case FEET -> InventoryTransfers.ArmorPiece.FEET.menuSlot();
            default -> -1;
        };
    }
}
