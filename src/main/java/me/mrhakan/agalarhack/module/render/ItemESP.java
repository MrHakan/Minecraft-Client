package me.mrhakan.agalarhack.module.render;

import java.util.List;
import java.util.Set;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.ItemIdList;
import me.mrhakan.agalarhack.services.NearestCandidates;
import me.mrhakan.agalarhack.services.ScannerService;
import me.mrhakan.agalarhack.services.scanning.ScanScheduler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Rarity;

/** Highlights dropped items, discovered on the shared tick budget rather than during rendering. */
public class ItemESP extends Module {
    private List<ItemEntity> items = List.of();
    private String parsedFilter;
    private Set<String> filter = Set.of();

    public ItemESP() {
        super("ItemESP", Category.RENDER, "Highlights dropped items with optional name, count and rarity colouring");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("range", 64.0, 8.0, 192.0, "Maximum render distance in blocks");
        addNumberSetting("maximumItems", 128, 8, 512, "Maximum rendered drops; nearest are kept when more are observed");
        addChoiceSetting("filterMode", "off", "Whether the id list hides or restricts drops", "off", "whitelist", "blacklist");
        settings.addSetting("filterItems", "");
        addBooleanSetting("boxes", true, "Draw a box around each drop");
        addBooleanSetting("labels", true, "Show the item name above each drop");
        addBooleanSetting("showCount", true, "Include the stack size in the label");
        addBooleanSetting("showDistance", false, "Include the distance in the label");
        addBooleanSetting("rarityColors", true, "Colour drops by item rarity instead of the fixed colour");
        addNumberSetting("red", 255.0, 0.0, 255.0, "Fixed overlay red channel");
        addNumberSetting("green", 220.0, 0.0, 255.0, "Fixed overlay green channel");
        addNumberSetting("blue", 60.0, 0.0, 255.0, "Fixed overlay blue channel");
        addNumberSetting("alpha", 220.0, 32.0, 255.0, "Overlay alpha channel");
    }

    public List<ItemEntity> items() { return items; }

    @Override
    public void onDisable() {
        service(ScannerService.class).cancel(this);
        items = List.of();
    }

    @Override
    public void onUpdate() {
        var level = mc.level;
        var player = mc.player;
        if (level == null || player == null) { items = List.of(); return; }
        var iterator = level.entitiesForRendering().iterator();
        var nearest = new NearestCandidates<ItemEntity>((int) getNumberSetting("maximumItems", 128));
        double rangeSquared = Math.pow(getNumberSetting("range", 64), 2);
        Set<String> ids = filterIds();
        String mode = getStringSetting("filterMode", "off");
        int[] considered = { 0 };
        service(ScannerService.class).offer(this, ScanScheduler.Priority.NEAR, 4097, budget -> {
            if (mc.level != level || mc.player != player) { items = List.of(); return ScanScheduler.Result.DONE; }
            if (considered[0] >= 4096 || !iterator.hasNext() || !budget.take(0, 0, 1)) {
                items = nearest.snapshot();
                return ScanScheduler.Result.DONE;
            }
            considered[0]++;
            var entity = iterator.next();
            if (entity instanceof ItemEntity drop && drop.isAlive() && !drop.getItem().isEmpty()) {
                double distance = player.distanceToSqr(drop);
                if (distance <= rangeSquared && allowed(drop, ids, mode)) nearest.add(drop, distance, drop.getId());
            }
            return ScanScheduler.Result.MORE;
        });
    }

    private boolean allowed(ItemEntity drop, Set<String> ids, String mode) {
        if (ids.isEmpty() || "off".equalsIgnoreCase(mode)) return true;
        String id = BuiltInRegistries.ITEM.getKey(drop.getItem().getItem()).toString();
        return "whitelist".equalsIgnoreCase(mode) == ids.contains(id);
    }

    /** Re-parsed only when the setting text changes; the list itself is bounded by ItemIdList. */
    private Set<String> filterIds() {
        String raw = getStringSetting("filterItems", "");
        if (!raw.equals(parsedFilter)) {
            filter = ItemIdList.parse(raw);
            parsedFilter = raw;
        }
        return filter;
    }

    /**
     * Vanilla rarity colours, so a drop reads the same as it does in a tooltip.
     *
     * <p>26.2 does not expose {@code ChatFormatting.getColor()}, so the legacy colour is resolved
     * through {@link net.minecraft.network.chat.TextColor} instead.
     */
    public static int rarityRgb(Rarity rarity) {
        if (rarity == null) return 0xFFFFFF;
        var color = net.minecraft.network.chat.TextColor.fromLegacyFormat(rarity.color());
        return color == null ? 0xFFFFFF : color.getValue() & 0x00FFFFFF;
    }
}
