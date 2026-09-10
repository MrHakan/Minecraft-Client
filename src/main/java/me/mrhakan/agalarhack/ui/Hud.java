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
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND
    };

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
        boolean showHealth = targetHud.getBooleanSetting("showHealth", true);
        boolean healthBar = targetHud.getBooleanSetting("healthBar", true);
        boolean showDistance = targetHud.getBooleanSetting("showDistance", true);
        boolean showArmor = targetHud.getBooleanSetting("showArmor", true) && target instanceof Player;
        boolean showEquipment = targetHud.getBooleanSetting("showEquipment", true);
        boolean showEffects = targetHud.getBooleanSetting("showEffects", true);

        List<String> lines = new ArrayList<>();
        lines.add(target.getName().getString());
        if (showHealth) {
            lines.add(String.format(Locale.ROOT, "HP %.1f / %.1f", target.getHealth(), target.getMaxHealth()));
        }
        if (showDistance) {
            lines.add(String.format(Locale.ROOT, "Distance %.1fm", mc.player.distanceTo(target)));
        }
        if (showArmor) {
            lines.add("Armor " + target.getArmorValue());
        }

        List<ItemStack> equipment = new ArrayList<>();
        if (showEquipment) {
            for (EquipmentSlot slot : TARGET_EQUIPMENT) {
                ItemStack item = target.getItemBySlot(slot);
                if (!item.isEmpty()) {
                    equipment.add(item);
                }
            }
        }

        List<MobEffectInstance> effects = new ArrayList<>();
        if (showEffects) {
            int limit = (int) Math.round(targetHud.getNumberSetting("maxEffects", 6.0));
            for (MobEffectInstance effect : target.getActiveEffects()) {
                if (effects.size() >= limit) {
                    break;
                }
                effects.add(effect);
            }
        }

        int contentWidth = lines.stream().mapToInt(font::width).max().orElse(80);
        int iconWidth = Math.max(equipment.size(), effects.size()) * 18;
        int boxWidth = Math.max(132, Math.max(contentWidth + 12, iconWidth + 12));
        int textHeight = lines.size() * font.lineHeight;
        int boxHeight = textHeight + 10;
        if (healthBar) {
            boxHeight += 7;
        }
        if (!equipment.isEmpty()) {
            boxHeight += 18;
        }
        if (!effects.isEmpty()) {
            boxHeight += 20;
        }

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
            double ratio = target.getMaxHealth() <= 0 ? 0 : target.getHealth() / target.getMaxHealth();
            ratio = Math.max(0.0, Math.min(1.0, ratio));
            int barX = x + 7;
            int barWidth = boxWidth - 14;
            graphics.fill(barX, cursorY + 1, barX + barWidth, cursorY + 5, 0xFF333333);
            int filled = (int) Math.round(barWidth * ratio);
            int barColor = ratio > 0.6 ? 0xFF55DD55 : ratio > 0.3 ? 0xFFFFCC44 : 0xFFFF5555;
            if (filled > 0) {
                graphics.fill(barX, cursorY + 1, barX + filled, cursorY + 5, barColor);
            }
            cursorY += 7;
        }

        if (!equipment.isEmpty()) {
            int itemX = x + 7;
            for (ItemStack item : equipment) {
                graphics.item(item, itemX, cursorY);
                itemX += 18;
            }
            cursorY += 18;
        }

        if (!effects.isEmpty()) {
            int effectX = x + 7;
            for (MobEffectInstance effect : effects) {
                graphics.blitSprite(
                        RenderPipelines.GUI_TEXTURED,
                        net.minecraft.client.gui.Hud.getMobEffectSprite(effect.getEffect()),
                        effectX,
                        cursorY + 1,
                        18,
                        18,
                        ARGB.white(1.0f));
                effectX += 18;
            }
        }
    }

    public static int rainbow(int delay) {
        double rainbowState = Math.ceil((System.currentTimeMillis() + delay) / 25.0);
        rainbowState %= 360;
        return Color.getHSBColor((float) (rainbowState / 360.0f), 1f, 1f).getRGB();
    }
}
