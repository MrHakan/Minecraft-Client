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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class Hud implements HudElement {

    public static class ModuleComparator implements Comparator<Module> {
        @Override
        public int compare(Module a, Module b) {
            Font font = Minecraft.getInstance().font;
            return Integer.compare(font.width(b.getDisplayName()), font.width(a.getDisplayName()));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || AgalarHackClient.moduleManager == null) {
            return;
        }
        renderBranding(graphics, mc);
        renderModuleList(graphics, mc);
        renderInfo(graphics, mc);
        renderTarget(graphics, mc);
    }

    private void renderBranding(GuiGraphicsExtractor graphics, Minecraft mc) {
        if (!AgalarHackClient.HUD_LAYOUT.get("branding").visible) {
            return;
        }
        Font font = mc.font;
        String text = AgalarHackClient.NAME + " " + AgalarHackClient.VERSION;
        int x = AgalarHackClient.HUD_LAYOUT.resolveX("branding", graphics.guiWidth(), font.width(text));
        int y = AgalarHackClient.HUD_LAYOUT.resolveY("branding", graphics.guiHeight(), font.lineHeight);
        graphics.text(font, AgalarHackClient.NAME, x, y, rainbow(0), true);
        graphics.text(font, AgalarHackClient.VERSION, x + font.width(AgalarHackClient.NAME) + 5, y, 0xFFFFFACD, true);
    }

    private void renderModuleList(GuiGraphicsExtractor graphics, Minecraft mc) {
        if (!AgalarHackClient.HUD_LAYOUT.get("modules").visible) {
            return;
        }
        Font font = mc.font;
        List<Module> enabled = new ArrayList<>();
        for (Module mod : AgalarHackClient.moduleManager.getModuleList()) {
            if (mod.isToggled()) {
                enabled.add(mod);
            }
        }
        enabled.sort(new ModuleComparator());
        if (enabled.isEmpty()) {
            return;
        }

        int width = enabled.stream().mapToInt(mod -> font.width(mod.getDisplayName())).max().orElse(0);
        int height = enabled.size() * font.lineHeight;
        int x = AgalarHackClient.HUD_LAYOUT.resolveX("modules", graphics.guiWidth(), width);
        int y = AgalarHackClient.HUD_LAYOUT.resolveY("modules", graphics.guiHeight(), height);

        int counter = 1;
        for (Module mod : enabled) {
            String name = mod.getDisplayName();
            graphics.text(font, name, x + width - font.width(name), y, rainbow(counter * 300), true);
            y += font.lineHeight;
            counter++;
        }
    }

    private void renderInfo(GuiGraphicsExtractor graphics, Minecraft mc) {
        if (!AgalarHackClient.HUD_LAYOUT.get("info").visible) {
            return;
        }
        Font font = mc.font;
        List<HudLine> lines = new ArrayList<>();
        for (Module mod : AgalarHackClient.moduleManager.getModuleList()) {
            if (mod.isToggled() && mod instanceof HudInfoProvider provider) {
                lines.addAll(provider.getHudLines());
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        int width = lines.stream().mapToInt(line -> font.width(line.text())).max().orElse(0);
        int height = lines.size() * font.lineHeight;
        int x = AgalarHackClient.HUD_LAYOUT.resolveX("info", graphics.guiWidth(), width);
        int y = AgalarHackClient.HUD_LAYOUT.resolveY("info", graphics.guiHeight(), height);
        for (HudLine line : lines) {
            graphics.text(font, line.text(), x, y, line.color(), true);
            y += font.lineHeight;
        }
    }

    private void renderTarget(GuiGraphicsExtractor graphics, Minecraft mc) {
        Module targetHud = AgalarHackClient.moduleManager.getModule("TargetHUD");
        if (targetHud == null || !targetHud.isToggled() || !AgalarHackClient.HUD_LAYOUT.get("target").visible) {
            return;
        }
        double timeout = targetHud.getNumberSetting("timeout", 3.0);
        LivingEntity target = AgalarHackClient.TARGET_TRACKER.get(timeout);
        if (target == null) {
            return;
        }

        Font font = mc.font;
        List<String> lines = new ArrayList<>();
        lines.add(target.getName().getString());
        if (targetHud.getBooleanSetting("showHealth", true)) {
            lines.add(String.format(Locale.ROOT, "HP %.1f / %.1f", target.getHealth(), target.getMaxHealth()));
        }
        if (targetHud.getBooleanSetting("showDistance", true)) {
            lines.add(String.format(Locale.ROOT, "Distance %.1fm", mc.player.distanceTo(target)));
        }
        if (targetHud.getBooleanSetting("showArmor", true) && target instanceof Player player) {
            lines.add("Armor " + player.getArmorValue());
        }

        int contentWidth = lines.stream().mapToInt(font::width).max().orElse(80);
        int boxWidth = Math.max(120, contentWidth + 12);
        int boxHeight = lines.size() * font.lineHeight + 10;
        int x = AgalarHackClient.HUD_LAYOUT.resolveX("target", graphics.guiWidth(), boxWidth);
        int y = AgalarHackClient.HUD_LAYOUT.resolveY("target", graphics.guiHeight(), boxHeight);
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xAA101010);
        graphics.fill(x, y, x + 3, y + boxHeight, 0xFF55AAFF);
        int textY = y + 5;
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), x + 7, textY, i == 0 ? 0xFFFFFFFF : 0xFFDDDDDD, true);
            textY += font.lineHeight;
        }
    }

    public static int rainbow(int delay) {
        double rainbowState = Math.ceil((System.currentTimeMillis() + delay) / 25.0);
        rainbowState %= 360;
        return Color.getHSBColor((float) (rainbowState / 360.0f), 1f, 1f).getRGB();
    }
}
