package me.mrhakan.agalarhack.ui;

import java.awt.Color;
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

        int width = 0;
        for (Module mod : enabledModules) width = Math.max(width, font.width(mod.getDisplayName()));
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
        List<String> lines = new ArrayList<>();
        lines.add(target.getName().getString());
        if (targetHud.getBooleanSetting("showHealth", true)) lines.add(String.format(Locale.ROOT, "HP %.1f / %.1f", target.getHealth(), target.getMaxHealth()));
        if (targetHud.getBooleanSetting("showDistance", true)) lines.add(String.format(Locale.ROOT, "Distance %.1fm", mc.player.distanceTo(target)));
        if (targetHud.getBooleanSetting("showArmor", true) && target instanceof Player) lines.add("Armor " + target.getArmorValue());

        List<ItemStack> equipment = new ArrayList<>();
        if (targetHud.getBooleanSetting("showEquipment", true)) {
            for (EquipmentSlot slot : TARGET_EQUIPMENT) {
                ItemStack item = target.getItemBySlot(slot);
                if (!item.isEmpty()) equipment.add(item);
            }
        }
        List<MobEffectInstance> effects = new ArrayList<>();
        if (targetHud.getBooleanSetting("showEffects", true)) {
            int limit = (int) Math.round(targetHud.getNumberSetting("maxEffects", 6.0));
            for (MobEffectInstance effect : target.getActiveEffects()) {
                if (effects.size() >= limit) break;
                effects.add(effect);
            }
        }

        int contentWidth = 80;
        for (String line : lines) contentWidth = Math.max(contentWidth, font.width(line));
        int boxWidth = Math.max(132, Math.max(contentWidth + 12, Math.max(equipment.size(), effects.size()) * 18 + 12));
        int boxHeight = lines.size() * font.lineHeight + 10 + (healthBar ? 7 : 0) + (!equipment.isEmpty() ? 18 : 0) + (!effects.isEmpty() ? 20 : 0);
        int x = AgalarHackClient.HUD_LAYOUT.resolveX("target", graphics.guiWidth(), boxWidth);
        int y = AgalarHackClient.HUD_LAYOUT.resolveY("target", graphics.guiHeight(), boxHeight);
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xB0101010);
        graphics.fill(x, y, x + 3, y + boxHeight, 0xFF55AAFF);

        int cursorY = y + 5;
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), x + 7, cursorY, i == 0 ? 0xFFFFFFFF : 0xFFDDDDDD, true);
            cursorY += font.lineHeight;
        }
        if (healthBar) {
            double ratio = target.getMaxHealth() <= 0 ? 0 : Math.max(0.0, Math.min(1.0, target.getHealth() / target.getMaxHealth()));
            int barX = x + 7, barWidth = boxWidth - 14;
            graphics.fill(barX, cursorY + 1, barX + barWidth, cursorY + 5, 0xFF333333);
            int filled = (int) Math.round(barWidth * ratio);
            int color = ratio > 0.6 ? 0xFF55DD55 : ratio > 0.3 ? 0xFFFFCC44 : 0xFFFF5555;
            if (filled > 0) graphics.fill(barX, cursorY + 1, barX + filled, cursorY + 5, color);
            cursorY += 7;
        }
        if (!equipment.isEmpty()) {
            int itemX = x + 7;
            for (ItemStack item : equipment) { graphics.item(item, itemX, cursorY); itemX += 18; }
            cursorY += 18;
        }
        if (!effects.isEmpty()) {
            int effectX = x + 7;
            for (MobEffectInstance effect : effects) {
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
