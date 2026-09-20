package me.mrhakan.agalarhack.ui;

import java.awt.Color;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.ui.hud.HudInfoProvider;
import me.mrhakan.agalarhack.ui.hud.HudLine;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class Hud implements HudElement {

    private static final EquipmentSlot[] TARGET_EQUIPMENT = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    private final List<Module> enabledModules = new ArrayList<>();
    private final List<HudLine> infoLines = new ArrayList<>();
    // Reused target-card buffers avoid allocating three backing lists every rendered frame.
    private final List<String> targetLines = new ArrayList<>(4);
    private final List<ItemStack> targetEquipment = new ArrayList<>(TARGET_EQUIPMENT.length);
    private final List<MobEffectInstance> targetEffects = new ArrayList<>(12);
    private final DecimalFormat oneDecimal = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.ROOT));

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || AgalarHackClient.moduleManager == null) return;
        renderBranding(graphics, mc);
        renderModuleList(graphics, mc);
        renderInfo(graphics, mc);
        renderTarget(graphics, mc);
    }

    private void renderBranding(GuiGraphicsExtractor graphics, Minecraft mc) {
        if (!AgalarHackClient.HUD_LAYOUT.get("branding").visible) return;
        Font font = mc.font;
        String text = AgalarHackClient.NAME + " " + AgalarHackClient.VERSION;
        int x = AgalarHackClient.HUD_LAYOUT.resolveX("branding", graphics.guiWidth(), font.width(text));
        int y = AgalarHackClient.HUD_LAYOUT.resolveY("branding", graphics.guiHeight(), font.lineHeight);
        graphics.text(font, AgalarHackClient.NAME, x, y, rainbow(0), true);
        graphics.text(font, AgalarHackClient.VERSION, x + font.width(AgalarHackClient.NAME) + 5, y, 0xFFFFFACD, true);
    }

    private void renderModuleList(GuiGraphicsExtractor graphics, Minecraft mc) {
        if (!AgalarHackClient.HUD_LAYOUT.get("modules").visible) return;
        Font font = mc.font;
        enabledModules.clear();
        for (Module mod : AgalarHackClient.moduleManager.getModuleList()) {
            if (mod.isToggled()) enabledModules.add(mod);
        }
        if (enabledModules.isEmpty()) return;
        enabledModules.sort(Comparator.comparingInt((Module mod) -> font.width(mod.getDisplayName())).reversed());

        int width = font.width(enabledModules.get(0).getDisplayName());
        int height = enabledModules.size() * font.lineHeight;
        int x = AgalarHackClient.HUD_LAYOUT.resolveX("modules", graphics.guiWidth(), width);
        int y = AgalarHackClient.HUD_LAYOUT.resolveY("modules", graphics.guiHeight(), height);

        int counter = 1;
        for (Module mod : enabledModules) {
            String name = mod.getDisplayName();
            graphics.text(font, name, x + width - font.width(name), y, rainbow(counter++ * 300), true);
            y += font.lineHeight;
        }
    }

    private void renderInfo(GuiGraphicsExtractor graphics, Minecraft mc) {
        if (!AgalarHackClient.HUD_LAYOUT.get("info").visible) return;
        Font font = mc.font;
        infoLines.clear();
        for (Module mod : AgalarHackClient.moduleManager.getModuleList()) {
            if (mod.isToggled() && mod instanceof HudInfoProvider provider) infoLines.addAll(provider.getHudLines());
        }
        if (infoLines.isEmpty()) return;
        int width = 0;
        for (HudLine line : infoLines) width = Math.max(width, font.width(line.text()));
        int height = infoLines.size() * font.lineHeight;
        int x = AgalarHackClient.HUD_LAYOUT.resolveX("info", graphics.guiWidth(), width);
        int y = AgalarHackClient.HUD_LAYOUT.resolveY("info", graphics.guiHeight(), height);
        for (HudLine line : infoLines) {
            graphics.text(font, line.text(), x, y, line.color(), true);
            y += font.lineHeight;
        }
    }

    private void renderTarget(GuiGraphicsExtractor graphics, Minecraft mc) {
        Module targetHud = AgalarHackClient.moduleManager.getModule("TargetHUD");
        if (targetHud == null || !targetHud.isToggled() || !AgalarHackClient.HUD_LAYOUT.get("target").visible) return;
        LivingEntity target = AgalarHackClient.TARGET_TRACKER.get(targetHud.getNumberSetting("timeout", 3.0));
        if (target == null) return;

        Font font = mc.font;
        boolean healthBar = targetHud.getBooleanSetting("healthBar", true);
        double maxHealth = target.getMaxHealth();
        double healthRatio = maxHealth <= 0 ? 0 : Math.max(0.0, Math.min(1.0, target.getHealth() / maxHealth));

        targetLines.clear();
        targetLines.add(target.getName().getString());
        if (targetHud.getBooleanSetting("showHealth", true)) {
            String health = "HP " + oneDecimal.format(target.getHealth()) + " / " + oneDecimal.format(maxHealth);
            if (targetHud.getBooleanSetting("healthPercent", true)) health += " (" + Math.round(healthRatio * 100.0) + "%)";
            targetLines.add(health);
        }
        if (targetHud.getBooleanSetting("showDistance", true)) targetLines.add("Distance " + oneDecimal.format(mc.player.distanceTo(target)) + "m");
        if (targetHud.getBooleanSetting("showArmor", true) && target instanceof Player) targetLines.add("Armor " + target.getArmorValue());

        targetEquipment.clear();
        if (targetHud.getBooleanSetting("showEquipment", true)) {
            for (EquipmentSlot slot : TARGET_EQUIPMENT) {
                ItemStack item = target.getItemBySlot(slot);
                if (!item.isEmpty()) targetEquipment.add(item);
            }
        }
        targetEffects.clear();
        if (targetHud.getBooleanSetting("showEffects", true)) {
            int limit = (int) Math.round(targetHud.getNumberSetting("maxEffects", 6.0));
            for (MobEffectInstance effect : target.getActiveEffects()) {
                if (targetEffects.size() >= limit) break;
                targetEffects.add(effect);
            }
        }

        int contentWidth = 80;
        for (String line : targetLines) contentWidth = Math.max(contentWidth, font.width(line));
        int boxWidth = Math.max(132, Math.max(contentWidth + 12, Math.max(targetEquipment.size(), targetEffects.size()) * 18 + 12));
        int boxHeight = targetLines.size() * font.lineHeight + 10 + (healthBar ? 7 : 0) + (!targetEquipment.isEmpty() ? 18 : 0) + (!targetEffects.isEmpty() ? 20 : 0);
        int x = AgalarHackClient.HUD_LAYOUT.resolveX("target", graphics.guiWidth(), boxWidth);
        int y = AgalarHackClient.HUD_LAYOUT.resolveY("target", graphics.guiHeight(), boxHeight);
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xB0101010);
        graphics.fill(x, y, x + 3, y + boxHeight, 0xFF55AAFF);

        int cursorY = y + 5;
        for (int i = 0; i < targetLines.size(); i++) {
            graphics.text(font, targetLines.get(i), x + 7, cursorY, i == 0 ? 0xFFFFFFFF : 0xFFDDDDDD, true);
            cursorY += font.lineHeight;
        }
        if (healthBar) {
            int barX = x + 7, barWidth = boxWidth - 14;
            graphics.fill(barX, cursorY + 1, barX + barWidth, cursorY + 5, 0xFF333333);
            int filled = (int) Math.round(barWidth * healthRatio);
            int color = healthRatio > 0.6 ? 0xFF55DD55 : healthRatio > 0.3 ? 0xFFFFCC44 : 0xFFFF5555;
            if (filled > 0) graphics.fill(barX, cursorY + 1, barX + filled, cursorY + 5, color);
            cursorY += 7;
        }
        if (!targetEquipment.isEmpty()) {
            int itemX = x + 7;
            for (ItemStack item : targetEquipment) { graphics.item(item, itemX, cursorY); itemX += 18; }
            cursorY += 18;
        }
        if (!targetEffects.isEmpty()) {
            int effectX = x + 7;
            for (MobEffectInstance effect : targetEffects) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, net.minecraft.client.gui.Hud.getMobEffectSprite(effect.getEffect()), effectX, cursorY + 1, 18, 18, ARGB.white(1.0f));
                effectX += 18;
            }
        }
    }

    public static int rainbow(int delay) {
        double rainbowState = Math.ceil((System.currentTimeMillis() + delay) / 25.0) % 360;
        return Color.getHSBColor((float) (rainbowState / 360.0f), 1f, 1f).getRGB();
    }
}
