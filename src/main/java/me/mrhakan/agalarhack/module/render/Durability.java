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
    private int cachedTick = Integer.MIN_VALUE;
    private long cachedSettings = Long.MIN_VALUE;

    public Durability() {
        super("Durability", Category.RENDER, "Shows remaining durability for equipped damageable items");
    }
    @Override public void selfSettings() {
        addBooleanSetting("showName", true, "Include the item's display name");
        addBooleanSetting("showPercent", true, "Include remaining durability as a percentage");
        addBooleanSetting("showExact", true, "Include exact remaining/max durability values");
        addBooleanSetting("showArmor", true, "Show durability for equipped armor pieces");
        addBooleanSetting("showOffhand", true, "Show a line for a damageable offhand item");
        addBooleanSetting("onlyWarnings", false, "Hide healthy items and show only warning or critical durability");
        addBooleanSetting("lowestOnly", false, "Show only the equipped damageable item with the lowest remaining percentage");
        addNumberSetting("warningPercent", 25.0, 1.0, 75.0, "Turn the HUD line amber at or below this remaining percentage");
        addNumberSetting("criticalPercent", 10.0, 1.0, 50.0, "Turn the HUD line red at or below this remaining percentage");
    }
    @Override public List<HudLine> getHudLines() {
        if (mc.player == null) {
            hudLines.clear();
            cachedTick = Integer.MIN_VALUE;
            return hudLines;
        }
        boolean showName = getBooleanSetting("showName", true);
        boolean showPercent = getBooleanSetting("showPercent", true);
        boolean showExact = getBooleanSetting("showExact", true);
        boolean showOffhand = getBooleanSetting("showOffhand", true);
        boolean showArmor = getBooleanSetting("showArmor", true);
        boolean onlyWarnings = getBooleanSetting("onlyWarnings", false);
        boolean lowestOnly = getBooleanSetting("lowestOnly", false);
        double warningPercent = getNumberSetting("warningPercent", 25.0);
        double criticalPercent = Math.min(warningPercent, getNumberSetting("criticalPercent", 10.0));
        long settings = settingsKey(showName, showPercent, showExact, showOffhand, showArmor, onlyWarnings,
                lowestOnly, warningPercent, criticalPercent);
        int tick = mc.player.tickCount;
        // HUD providers can be queried every rendered frame. Equipment durability only changes on game ticks,
        // so avoid rebuilding names/strings multiple times during the same tick.
        if (tick == cachedTick && settings == cachedSettings) return hudLines;
        cachedTick = tick;
        cachedSettings = settings;
        hudLines.clear();

        if (lowestOnly) {
            Candidate lowest = candidate(mc.player.getMainHandItem(), "Main");
            if (showOffhand) lowest = lower(lowest, candidate(mc.player.getOffhandItem(), "Offhand"));
            if (showArmor) for (int i = 0; i < ARMOR_SLOTS.length; i++)
                lowest = lower(lowest, candidate(mc.player.getItemBySlot(ARMOR_SLOTS[i]), ARMOR_LABELS[i]));
            if (lowest != null) appendLine(lowest.stack(), lowest.slot(), showName, showPercent, showExact,
                    onlyWarnings, warningPercent, criticalPercent);
            return hudLines;
        }

        appendLine(mc.player.getMainHandItem(), "Main", showName, showPercent, showExact, onlyWarnings, warningPercent, criticalPercent);
        if (showOffhand) appendLine(mc.player.getOffhandItem(), "Offhand", showName, showPercent, showExact, onlyWarnings, warningPercent, criticalPercent);
        if (showArmor) {
            for (int i = 0; i < ARMOR_SLOTS.length; i++) {
                appendLine(mc.player.getItemBySlot(ARMOR_SLOTS[i]), ARMOR_LABELS[i], showName, showPercent, showExact,
                        onlyWarnings, warningPercent, criticalPercent);
            }
        }
        return hudLines;
    }
    private void appendLine(ItemStack stack, String slot, boolean showName, boolean showPercent, boolean showExact,
            boolean onlyWarnings, double warningPercent, double criticalPercent) {
        Candidate candidate = candidate(stack, slot);
        if (candidate == null || (onlyWarnings && candidate.percent() > warningPercent)) return;
        int maxDamage = stack.getMaxDamage();
        int remaining = Math.max(0, maxDamage - stack.getDamageValue());
        int percent = candidate.percent();

        text.setLength(0);
        text.append("Durability [").append(slot).append("]: ");
        if (showName) text.append(stack.getHoverName().getString()).append(' ');
        if (showExact) text.append(remaining).append('/').append(maxDamage);
        if (showPercent) {
            if (showExact) text.append(' ');
            text.append('(').append(percent).append("%)");
        }
        if (!showExact && !showPercent) text.append(remaining).append(" left");
        int color = percent <= criticalPercent ? CRITICAL_COLOR
                : percent <= warningPercent ? WARNING_COLOR : NORMAL_COLOR;
        hudLines.add(new HudLine(text.toString(), color));
    }
    private static Candidate candidate(ItemStack stack, String slot) {
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) return null;
        int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        int percent = (int) Math.round(remaining * 100.0 / stack.getMaxDamage());
        return new Candidate(stack, slot, percent);
    }
    private static Candidate lower(Candidate current, Candidate next) {
        return next == null || (current != null && current.percent() <= next.percent()) ? current : next;
    }
    private static long settingsKey(boolean showName, boolean showPercent, boolean showExact, boolean showOffhand,
            boolean showArmor, boolean onlyWarnings, boolean lowestOnly, double warning, double critical) {
        long flags = (showName ? 1L : 0L) | (showPercent ? 2L : 0L) | (showExact ? 4L : 0L)
                | (showOffhand ? 8L : 0L) | (showArmor ? 16L : 0L) | (onlyWarnings ? 32L : 0L)
                | (lowestOnly ? 64L : 0L);
        return flags ^ (Double.doubleToLongBits(warning) * 31L) ^ (Double.doubleToLongBits(critical) * 17L);
    }
    @Override public void onDisable() { cachedTick = Integer.MIN_VALUE; hudLines.clear(); }
    private record Candidate(ItemStack stack, String slot, int percent) {}
}
