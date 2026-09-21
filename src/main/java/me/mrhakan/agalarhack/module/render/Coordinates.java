package me.mrhakan.agalarhack.module.render;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.FieldPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.ui.hud.HudInfoProvider;
import me.mrhakan.agalarhack.ui.hud.HudLine;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

public class Coordinates extends Module implements HudInfoProvider {
    private static final int TEXT_COLOR = 0xFFF0F0F0;
    private final List<HudLine> hudLines = new ArrayList<>(2);
    private final StringBuffer text = new StringBuffer(112);
    private final DecimalFormat[] formats = createFormats();
    private final FieldPosition fieldPosition = new FieldPosition(0);

    public Coordinates() {
        super("Coordinates", Category.RENDER, "Shows XYZ, heading, chunk position, and optional Nether/Overworld coordinate conversion");
    }

    @Override public void selfSettings() {
        addNumberSetting("precision", 1.0, 0.0, 3.0, "Number of decimal places shown for coordinates");
        addBooleanSetting("facing", true, "Append the horizontal cardinal direction");
        addBooleanSetting("headingDegrees", false, "Append a 0-359 degree heading for precise navigation");
        addBooleanSetting("chunkCoords", false, "Append the current chunk X/Z for navigation and chunk-aligned building");
        addBooleanSetting("chunkLocal", false, "Show the current block position inside the 16x16 chunk");
        addBooleanSetting("dimensionCoords", true, "Show the matching Nether or Overworld X/Z coordinates");
        addBooleanSetting("dimensionLine", false, "Put converted dimension coordinates on a separate HUD line for readability");
    }

    @Override public List<HudLine> getHudLines() {
        hudLines.clear();
        if (mc.player == null || mc.level == null) return hudLines;
        int precision = Math.max(0, Math.min(3, (int) Math.round(getNumberSetting("precision", 1.0))));
        boolean facing = getBooleanSetting("facing", true);
        boolean heading = getBooleanSetting("headingDegrees", false);
        boolean chunks = getBooleanSetting("chunkCoords", false);
        boolean local = getBooleanSetting("chunkLocal", false);
        boolean dimensions = getBooleanSetting("dimensionCoords", true);
        boolean separateDimension = getBooleanSetting("dimensionLine", false);
        DecimalFormat format = formats[precision];
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        int blockX = Mth.floor(x), blockZ = Mth.floor(z);
        float yaw = mc.player.getYRot();

        text.setLength(0);
        text.append("XYZ: "); appendNumber(format, x); text.append(" / "); appendNumber(format, y); text.append(" / "); appendNumber(format, z);
        if (facing || heading) {
            text.append(" | ");
            if (facing) text.append(getFacing(yaw));
            if (facing && heading) text.append(' ');
            if (heading) text.append(getHeadingDegrees(yaw)).append(" deg");
        }
        if (chunks) text.append(" | Chunk: ").append(blockX >> 4).append(" / ").append(blockZ >> 4);
        if (local) text.append(" | Local: ").append(blockX & 15).append(" / ").append(blockZ & 15);

        boolean nether = mc.level.dimension() == Level.NETHER;
        boolean overworld = mc.level.dimension() == Level.OVERWORLD;
        boolean showDimension = dimensions && (nether || overworld);
        boolean dimensionLine = showDimension && separateDimension;
        if (showDimension && !dimensionLine) appendDimension(format, x, z, nether);
        hudLines.add(new HudLine(text.toString(), TEXT_COLOR));
        if (dimensionLine) {
            text.setLength(0);
            appendDimension(format, x, z, nether);
            hudLines.add(new HudLine(text.toString(), TEXT_COLOR));
        }
        return hudLines;
    }

    private void appendDimension(DecimalFormat format, double x, double z, boolean nether) {
        double scale = nether ? 8.0 : 0.125;
        if (text.length() > 0) text.append(" | ");
        text.append(nether ? "OW" : "Nether").append(": ");
        appendNumber(format, x * scale); text.append(" / "); appendNumber(format, z * scale);
    }
    private void appendNumber(DecimalFormat format, double value) {
        fieldPosition.setBeginIndex(0); fieldPosition.setEndIndex(0);
        format.format(value, text, fieldPosition);
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
    private static int getHeadingDegrees(float yaw) {
        return Mth.floor(((yaw % 360.0F) + 360.0F) % 360.0F);
    }
    private static String getFacing(float yaw) {
        double normalized = ((yaw % 360.0) + 360.0) % 360.0;
        if (normalized >= 315.0 || normalized < 45.0) return "S";
        if (normalized < 135.0) return "W";
        if (normalized < 225.0) return "N";
        return "E";
    }
}
