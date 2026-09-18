package me.mrhakan.agalarhack.module.render;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.ui.hud.HudInfoProvider;
import me.mrhakan.agalarhack.ui.hud.HudLine;
import net.minecraft.world.level.Level;

public class Coordinates extends Module implements HudInfoProvider {

    private static final int TEXT_COLOR = 0xFFF0F0F0;
    private final List<HudLine> hudLines = new ArrayList<>(1);
    private final StringBuilder text = new StringBuilder(96);
    private final DecimalFormat[] formats = createFormats();

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
        hudLines.clear();
        if (mc.player == null || mc.level == null) return hudLines;

        int precision = Math.max(0, Math.min(3, (int) Math.round(getNumberSetting("precision", 1.0))));
        DecimalFormat format = formats[precision];
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        text.setLength(0);
        text.append("XYZ: ");
        appendNumber(format, x);
        text.append(" / ");
        appendNumber(format, y);
        text.append(" / ");
        appendNumber(format, z);

        if (getBooleanSetting("facing", true)) {
            text.append(" | ").append(getFacing(mc.player.getYRot()));
        }

        if (getBooleanSetting("dimensionCoords", true)) {
            boolean nether = mc.level.dimension() == Level.NETHER;
            boolean overworld = mc.level.dimension() == Level.OVERWORLD;
            if (nether || overworld) {
                double scale = nether ? 8.0 : 0.125;
                text.append(" | ").append(nether ? "OW" : "Nether").append(": ");
                appendNumber(format, x * scale);
                text.append(" / ");
                appendNumber(format, z * scale);
            }
        }

        hudLines.add(new HudLine(text.toString(), TEXT_COLOR));
        return hudLines;
    }

    private void appendNumber(DecimalFormat format, double value) {
        format.format(value, text, new java.text.FieldPosition(0));
    }

    private static DecimalFormat[] createFormats() {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        DecimalFormat[] result = new DecimalFormat[4];
        for (int precision = 0; precision < result.length; precision++) {
            StringBuilder pattern = new StringBuilder("0");
            if (precision > 0) {
                pattern.append('.');
                for (int i = 0; i < precision; i++) pattern.append('0');
            }
            DecimalFormat format = new DecimalFormat(pattern.toString(), symbols);
            format.setGroupingUsed(false);
            format.setRoundingMode(RoundingMode.HALF_UP);
            result[precision] = format;
        }
        return result;
    }

    private static String getFacing(float yaw) {
        double normalized = ((yaw % 360.0) + 360.0) % 360.0;
        if (normalized >= 315.0 || normalized < 45.0) return "S";
        if (normalized < 135.0) return "W";
        if (normalized < 225.0) return "N";
        return "E";
    }
}
