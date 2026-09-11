package me.mrhakan.agalarhack.module.player;

import java.util.Set;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.InventoryService;
import me.mrhakan.agalarhack.services.InventoryTransfers;
import me.mrhakan.agalarhack.services.ItemIdList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * Drops items the player has explicitly listed as junk.
 *
 * <p>This is the one automation here that destroys property, so it is whitelist-driven rather than
 * blacklist-driven: nothing is dropped unless its registry id appears in the list. Enchanted and
 * renamed items are additionally protected by default even when listed, and the hotbar is left
 * alone, so a misconfigured list cannot quietly throw away what the player is holding.
 */
public class InventoryCleaner extends Module {
    private static final String OWNER = "inventorycleaner";
    /** The lowest priority here: dropping junk always yields to anything else. */
    private static final int PRIORITY = 10;

    private static final String DEFAULT_JUNK = "rotten_flesh, poisonous_potato, spider_eye, "
            + "wheat_seeds, melon_seeds, pumpkin_seeds, beetroot_seeds";

    private int cooldown;
    private String parsedFrom;
    private Set<String> junk = Set.of();

    public InventoryCleaner() {
        super("InventoryCleaner", Category.PLAYER, "Drops only the items you list as junk, protecting enchanted and named gear");
    }

    @Override
    public void selfSettings() {
        settings.addSetting("junk", DEFAULT_JUNK);
        addNumberSetting("delay", 10, 1, 100, "Ticks to wait between drops");
        addBooleanSetting("includeHotbar", false, "Also clean the hotbar; off by default so held items are safe");
        addBooleanSetting("keepEnchanted", true, "Never drop enchanted items, even if listed");
        addBooleanSetting("keepNamed", true, "Never drop renamed or custom items, even if listed");
        addBooleanSetting("wholeStack", true, "Drop the whole stack rather than one item at a time");
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

        Set<String> listed = junkList();
        if (listed.isEmpty()) return;

        int first = getBooleanSetting("includeHotbar", false) ? 0 : InventoryTransfers.HOTBAR_SIZE;
        for (int slot = first; slot < InventoryTransfers.INVENTORY_SIZE; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!shouldDrop(stack, listed)) continue;
            if (transfers.dropSlot(OWNER, PRIORITY, InventoryTransfers.menuSlot(slot),
                    getBooleanSetting("wholeStack", true))) {
                cooldown = (int) Math.round(getNumberSetting("delay", 10));
            }
            return;
        }
    }

    private boolean shouldDrop(ItemStack stack, Set<String> listed) {
        if (stack.isEmpty()) return false;
        if (getBooleanSetting("keepNamed", true) && stack.has(DataComponents.CUSTOM_NAME)) return false;
        if (getBooleanSetting("keepEnchanted", true)) {
            var enchantments = stack.get(DataComponents.ENCHANTMENTS);
            if (enchantments != null && !enchantments.isEmpty()) return false;
        }
        return listed.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    /** Re-parsed only when the setting text actually changes. */
    private Set<String> junkList() {
        String raw = getStringSetting("junk", DEFAULT_JUNK);
        if (!raw.equals(parsedFrom)) {
            junk = ItemIdList.parse(raw);
            parsedFrom = raw;
        }
        return junk;
    }
}
