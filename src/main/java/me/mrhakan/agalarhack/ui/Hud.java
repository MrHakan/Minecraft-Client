package me.mrhakan.agalarhack.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public class Hud {

	public static class ModuleComparator implements Comparator<Module> {

		@Override
		public int compare(Module arg0, Module arg1) {
			TextRenderer tr = MinecraftClient.getInstance().textRenderer;
			return Integer.compare(tr.getWidth(arg1.getName()), tr.getWidth(arg0.getName()));
		}
	}

	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || AgalarHackClient.moduleManager == null) {
			return;
		}
		TextRenderer tr = mc.textRenderer;

		//client name + version
		context.drawTextWithShadow(tr, AgalarHackClient.NAME, 2, 2, rainbow(0));
		context.drawTextWithShadow(tr, AgalarHackClient.VERSION, tr.getWidth(AgalarHackClient.NAME) + 6, 2, 0xFFFFFACD);

		//arraylist of enabled modules, widest first
		List<Module> enabled = new ArrayList<>();
		for (Module mod : AgalarHackClient.moduleManager.getModuleList()) {
			if (mod.isToggled()) {
				enabled.add(mod);
			}
		}
		enabled.sort(new ModuleComparator());

		int screenWidth = context.getScaledWindowWidth();
		int y = 2;
		int counter = 1;
		for (Module mod : enabled) {
			context.drawTextWithShadow(tr, mod.getName(), screenWidth - tr.getWidth(mod.getName()) - 2, y, rainbow(counter * 300));
			y += tr.fontHeight;
			counter++;
		}
	}

	public static int rainbow(int delay) {
		double rainbowState = Math.ceil((System.currentTimeMillis() + delay) / 25.0);
		rainbowState %= 360;
		return Color.getHSBColor((float) (rainbowState / 360.0f), 1f, 1f).getRGB();
	}
}
