package me.mrhakan.agalarhack.module.render;

import java.util.List;
import java.util.Locale;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.ui.hud.HudInfoProvider;
import me.mrhakan.agalarhack.ui.hud.HudLine;

public class Coordinates extends Module implements HudInfoProvider {

	public Coordinates() {
		super("Coordinates", Category.RENDER, "Shows your current XYZ coordinates and optional facing direction on the HUD");
	}

	@Override
	public void selfSettings() {
		addNumberSetting("precision", 1.0, 0.0, 3.0, "Number of decimal places shown for coordinates");
		addBooleanSetting("facing", true, "Append the horizontal cardinal direction");
	}

	@Override
	public List<HudLine> getHudLines() {
		if (mc.player == null) {
			return List.of();
		}
		int precision = (int) Math.round(getNumberSetting("precision", 1.0));
		String number = "%." + precision + "f";
		String text = "XYZ: "
				+ String.format(Locale.ROOT, number, mc.player.getX()) + " / "
				+ String.format(Locale.ROOT, number, mc.player.getY()) + " / "
				+ String.format(Locale.ROOT, number, mc.player.getZ());
		if (getBooleanSetting("facing", true)) {
			text += " | " + getFacing(mc.player.getYRot());
		}
		return List.of(new HudLine(text, 0xFFF0F0F0));
	}

	private static String getFacing(float yaw) {
		double normalized = ((yaw % 360.0) + 360.0) % 360.0;
		if (normalized >= 315.0 || normalized < 45.0) {
			return "S";
		}
		if (normalized < 135.0) {
			return "W";
		}
		if (normalized < 225.0) {
			return "N";
		}
		return "E";
	}
}
