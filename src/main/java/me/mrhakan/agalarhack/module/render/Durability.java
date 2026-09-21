package me.mrhakan.agalarhack.module.render;

import java.util.ArrayList;
import java.util.List;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.ui.hud.HudInfoProvider;
import me.mrhakan.agalarhack.ui.hud.HudLine;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class Durability extends Module implements HudInfoProvider {
    private static final int NORMAL_COLOR = 0xFFF0F0F0;
    private static final int WARNING_COLOR = 0xFFFFAA00;
    private static final int CRITICAL_COLOR = 0xFFFF5555;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final String[] ARMOR_LABELS = { "Head", "Chest", "Legs", "Feet" };
    private final List<HudLine> hudLines = new ArrayList<>(6);
    private final StringBuilder text = new StringBuilder(96);

    public Durability() {
        super("Durability", Category.RENDER, "Shows remaining durability for equipped damageable items");
    }
    @Override public void selfSettings() {
        addBooleanSetting("showName", true, "Include the item's display name");
        addBooleanSetting("showPercent", true, "Include remaining durability as a percentage");
        addBooleanSetting("showArmor", true, "Show durability for equipped armor pieces");
        addBooleanSetting("showOffhand", true, "Show a line for a damageable offhand item");
        addBooleanSetting("onlyWarnings", false, "Hide healthy items and show only warning or critical durability");
        addNumberSetting("warningPercent", 25.0, 1.0, 75.0, "Turn the HUD line amber at or below this remaining percentage");
        addNumberSetting("criticalPercent", 10.0, 1.0, 50.0, "Turn the HUD line red at or below this remaining percentage");
    }
    @Override public List<HudLine> getHudLines() {
        hudLines.clear();
        if (mc.player == null) return hudLines;
        boolean showName = getBooleanSetting("showName", true);
        boolean showPercent = getBooleanSetting("showPercent", true);
        boolean showOffhand = getBooleanSetting("showOffhand", true);
        boolean showArmor = getBooleanSetting("showArmor", true);
        boolean onlyWarnings = getBooleanSetting("onlyWarnings", false);
        double warningPercent = getNumberSetting("warningPercent", 25.0);
        double criticalPercent = Math.min(warningPercent, getNumberSetting("criticalPercent", 10.0));

        appendLine(mc.player.getMainHandItem(), "Main", showName, showPercent, onlyWarnings, warningPercent, criticalPercent);
        if (showOffhand) appendLine(mc.player.getOffhandItem(), "Offhand", showName, showPercent, onlyWarnings, warningPercent, criticalPercent);
        if (showArmor) {
            for (int i = 0; i < ARMOR_SLOTS.length; i++) {
                appendLine(mc.player.getItemBySlot(ARMOR_SLOTS[i]), ARMOR_LABELS[i], showName, showPercent,
                        onlyWarnings, warningPercent, criticalPercent);
            }
        }
        return hudLines;
    }
    private void appendLine(ItemStack stack, String slot, boolean showName, boolean showPercent,
            boolean onlyWarnings, double warningPercent, double criticalPercent) {
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) return;
        int maxDamage = stack.getMaxDamage();
        int remaining = Math.max(0, maxDamage - stack.getDamageValue());
        int percent = (int) Math.round(remaining * 100.0 / maxDamage);
        if (onlyWarnings && percent > warningPercent) return;

        text.setLength(0);
        text.append("Durability [").append(slot).append("]: ");
        if (showName) text.append(stack.getHoverName().getString()).append(' ');
        text.append(remaining).append('/').append(maxDamage);
        if (showPercent) text.append(" (").append(percent).append("%)");
        int color = percent <= criticalPercent ? CRITICAL_COLOR
                : percent <= warningPercent ? WARNING_COLOR : NORMAL_COLOR;
        hudLines.add(new HudLine(text.toString(), color));
    }
}
