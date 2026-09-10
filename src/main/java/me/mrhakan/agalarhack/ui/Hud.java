package me.mrhakan.agalarhack.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.ui.hud.HudInfoProvider;
import me.mrhakan.agalarhack.ui.hud.HudLine;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class Hud implements HudElement {

	public static class ModuleComparator implements Comparator<Module> {

		@Override
		public int compare(Module arg0, Module arg1) {
			Font font = Minecraft.getInstance().font;
			return Integer.compare(font.width(arg1.getDisplayName()), font.width(arg0.getDisplayName()));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || AgalarHackClient.moduleManager == null) {
			return;
		}
		Font font = mc.font;

		// Client name + version.
		extractor.text(font, AgalarHackClient.NAME, 2, 2, rainbow(0), true);
		extractor.text(font, AgalarHackClient.VERSION, font.width(AgalarHackClient.NAME) + 6, 2, 0xFFFFFACD, true);

		// Array list of enabled modules, widest first.
		List<Module> enabled = new ArrayList<>();
		for (Module mod : AgalarHackClient.moduleManager.getModuleList()) {
			if (mod.isToggled()) {
				enabled.add(mod);
			}
		}
		enabled.sort(new ModuleComparator());

		int screenWidth = extractor.guiWidth();
		int y = 2;
		int counter = 1;
		for (Module mod : enabled) {
			String displayName = mod.getDisplayName();
			extractor.text(font, displayName, screenWidth - font.width(displayName) - 2, y, rainbow(counter * 300), true);
			y += font.lineHeight;
			counter++;
		}

		// HUD-info modules render through a shared provider contract instead of
		// hard-coded module name checks. New info modules can plug in without
		// changing this renderer.
		List<HudLine> infoLines = new ArrayList<>();
		for (Module mod : AgalarHackClient.moduleManager.getModuleList()) {
			if (mod.isToggled() && mod instanceof HudInfoProvider provider) {
				infoLines.addAll(provider.getHudLines());
			}
		}

		int infoY = extractor.guiHeight() - 2;
		for (HudLine line : infoLines) {
			infoY -= font.lineHeight;
			extractor.text(font, line.text(), 2, infoY, line.color(), true);
		}
	}

	public static int rainbow(int delay) {
		double rainbowState = Math.ceil((System.currentTimeMillis() + delay) / 25.0);
		rainbowState %= 360;
		return Color.getHSBColor((float) (rainbowState / 360.0f), 1f, 1f).getRGB();
	}
}
