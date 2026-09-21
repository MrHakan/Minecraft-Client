package me.mrhakan.agalarhack.module.render;

import java.util.List;
import java.util.Locale;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.ui.hud.HudInfoProvider;
import me.mrhakan.agalarhack.ui.hud.HudLine;
import net.minecraft.world.level.Level;

public class Coordinates extends Module implements HudInfoProvider {

    public Coordinates() {
        super("Coordinates", Category.RENDER, "Shows XYZ, facing direction, and optional Nether/Overworld coordinate conversion");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("precision", 1.0, 0.0, 3.0, "Number of decimal places shown for coordinates");
        addBooleanSetting("facing", true, "Append the horizontal cardinal direction");
        addBooleanSetting("dimensionCoords", true, "Show the matching Nether or Overworld X/Z coordinates");
    }

    @Override
    public List<HudLine> getHudLines() {
        if (mc.player == null || mc.level == null) return List.of();
        int precision = (int) Math.round(getNumberSetting("precision", 1.0));
        String number = "%." + precision + "f";
        StringBuilder text = new StringBuilder("XYZ: ")
                .append(String.format(Locale.ROOT, number, mc.player.getX())).append(" / ")
                .append(String.format(Locale.ROOT, number, mc.player.getY())).append(" / ")
                .append(String.format(Locale.ROOT, number, mc.player.getZ()));
        if (getBooleanSetting("facing", true)) text.append(" | ").append(getFacing(mc.player.getYRot()));

        if (getBooleanSetting("dimensionCoords", true)) {
            boolean nether = mc.level.dimension() == Level.NETHER;
            boolean overworld = mc.level.dimension() == Level.OVERWORLD;
            if (nether || overworld) {
                double scale = nether ? 8.0 : 0.125;
                String label = nether ? "OW" : "Nether";
                text.append(" | ").append(label).append(": ")
                        .append(String.format(Locale.ROOT, number, mc.player.getX() * scale)).append(" / ")
                        .append(String.format(Locale.ROOT, number, mc.player.getZ() * scale));
            }
        }
        return List.of(new HudLine(text.toString(), 0xFFF0F0F0));
    }

    private static String getFacing(float yaw) {
        double normalized = ((yaw % 360.0) + 360.0) % 360.0;
        if (normalized >= 315.0 || normalized < 45.0) return "S";
        if (normalized < 135.0) return "W";
        if (normalized < 225.0) return "N";
        return "E";
    }
}
