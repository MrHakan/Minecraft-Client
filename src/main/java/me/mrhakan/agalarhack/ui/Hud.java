package me.mrhakan.agalarhack.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import me.mrhakan.agalarhack.Main;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class Hud extends Gui {

	private Minecraft mc = Minecraft.getMinecraft();

	public static class ModuleComparator implements Comparator<Module> {

		@Override
		public int compare(Module arg0, Module arg1) {
			FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
			return Integer.compare(fr.getStringWidth(arg1.getName()), fr.getStringWidth(arg0.getName()));
		}
	}

	@SubscribeEvent
	public void renderOverlay(RenderGameOverlayEvent.Text event) {
		if (Main.moduleManager == null) {
			return;
		}
		ScaledResolution sr = new ScaledResolution(mc);
		FontRenderer fr = mc.fontRenderer;

		//client name + version
		fr.drawStringWithShadow(Main.name, 2, 2, rainbow(0));
		fr.drawStringWithShadow(Main.currentvers, fr.getStringWidth(Main.name) + 6, 2, 0xfffffacd);

		//arraylist of enabled modules, widest first
		List<Module> enabled = new ArrayList<>();
		for (Module mod : Main.moduleManager.getModuleList()) {
			if (mod.isToggled()) {
				enabled.add(mod);
			}
		}
		enabled.sort(new ModuleComparator());

		int y = 2;
		int counter = 1;
		for (Module mod : enabled) {
			fr.drawStringWithShadow(mod.getName(), sr.getScaledWidth() - fr.getStringWidth(mod.getName()) - 2, y, rainbow(counter * 300));
			y += fr.FONT_HEIGHT;
			counter++;
		}
	}

	public static int rainbow(int delay) {
		double rainbowState = Math.ceil((System.currentTimeMillis() + delay) / 25.0);
		rainbowState %= 360;
		return Color.getHSBColor((float) (rainbowState / 360.0f), 1f, 1f).getRGB();
	}
}
