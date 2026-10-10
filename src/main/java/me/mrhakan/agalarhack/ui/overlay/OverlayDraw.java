package me.mrhakan.agalarhack.ui.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Locale;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.RainbowColors;
import me.mrhakan.agalarhack.services.StableIds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Line geometry and colour helpers shared by every world overlay family.
 *
 * <p>All overlays draw into the one {@code RenderTypes.lines()} buffer, so a box is twelve lines
 * and a marker five; nothing here owns state.
 */
final class OverlayDraw {
    private OverlayDraw() {
    }

    static int dimRgb(int rgb, double factor) {
        factor = Math.max(0.0, Math.min(1.0, factor));
        int r = (int) Math.round(((rgb >> 16) & 0xFF) * factor);
        int g = (int) Math.round(((rgb >> 8) & 0xFF) * factor);
        int b = (int) Math.round((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    /** Cached per block type: this runs for every marker on every frame. */
    static String blockId(Minecraft mc, BlockPos pos) {
        return StableIds.block(mc.level.getBlockState(pos).getBlock());
    }

    static double blockDistance(Minecraft mc, BlockPos pos) {
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dy = pos.getY() + 0.5 - mc.player.getY();
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static int fadedAlpha(double alphaSetting, double distance, double range, boolean enabled) {
        int alpha = clampChannel(alphaSetting);
        if (!enabled || range <= 0.0) return alpha;
        double factor = Math.max(0.18, Math.min(1.0, 1.0 - distance / range));
        return Math.max(24, Math.min(255, (int) Math.round(alpha * factor)));
    }

    static void marker(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, double size, int color) {
        line(buffer, pose, p.x - size, p.y, p.z, p.x + size, p.y, p.z, color);
        line(buffer, pose, p.x, p.y - size, p.z, p.x, p.y + size, p.z, color);
        line(buffer, pose, p.x, p.y, p.z - size, p.x, p.y, p.z + size, color);
        line(buffer, pose, p.x - size * 0.7, p.y, p.z - size * 0.7, p.x + size * 0.7, p.y, p.z + size * 0.7, color);
        line(buffer, pose, p.x - size * 0.7, p.y, p.z + size * 0.7, p.x + size * 0.7, p.y, p.z - size * 0.7, color);
    }

    static void box(VertexConsumer buffer, PoseStack.Pose pose, AABB b, int color) {
        line(buffer, pose, b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, color);
        line(buffer, pose, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, color);
        line(buffer, pose, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ, color);
        line(buffer, pose, b.minX, b.minY, b.maxZ, b.minX, b.minY, b.minZ, color);
        line(buffer, pose, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, color);
        line(buffer, pose, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, color);
        line(buffer, pose, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ, color);
        line(buffer, pose, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ, color);
        line(buffer, pose, b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, color);
        line(buffer, pose, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, color);
        line(buffer, pose, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, color);
        line(buffer, pose, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, color);
    }

    static void line(VertexConsumer buffer, PoseStack.Pose pose, double ax, double ay, double az, double bx, double by, double bz, int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF, a = (color >>> 24) & 0xFF;
        float nx = (float) (bx - ax), ny = (float) (by - ay), nz = (float) (bz - az);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0.0001f) { nx /= len; ny /= len; nz /= len; }
        buffer.addVertex(pose, (float) ax, (float) ay, (float) az).setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(1.0f);
        buffer.addVertex(pose, (float) bx, (float) by, (float) bz).setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(1.0f);
    }

    /**
     * The module's configured colour, cycling through the hues when it asked to.
     *
     * <p>{@code nanoTime} rather than wall-clock time: a clock adjustment mid-session would make the
     * cycle jump, and nothing here needs to agree with any other machine. The phase lets one trail
     * or one set of tracers spread along the cycle instead of every segment flashing together.
     */
    static int moduleRgb(Module module, double red, double green, double blue, double phase) {
        int base = rgb(red, green, blue);
        if (!RainbowColors.enabled(module)) return base;
        return RainbowColors.cycle(module, System.nanoTime() / 1_000_000L, base, phase);
    }

    /**
     * Appends {@code String.format(Locale.ROOT, "%.1f", value)}.
     *
     * <p>ESP labels format a distance (and optionally health) for every target on every frame, and a
     * {@code Formatter} per call was a large part of what each label cost. Values that round cleanly
     * are formatted directly; anything within a hair of a tie, negative, very large or not finite is
     * left to {@code String.format}, so the text is always exactly what it was.
     */
    static StringBuilder appendTenths(StringBuilder out, double value) {
        // The sign bit, not value >= 0: String.format writes -0.0 for negative zero.
        if (Double.doubleToRawLongBits(value) >= 0 && value < 1.0E6) {
            double scaled = value * 10.0;
            double floor = Math.floor(scaled);
            double fraction = scaled - floor;
            if (Math.abs(fraction - 0.5) > 1.0E-6) {
                long tenths = (long) floor + (fraction > 0.5 ? 1 : 0);
                return out.append(tenths / 10).append('.').append(tenths % 10);
            }
        }
        return out.append(String.format(Locale.ROOT, "%.1f", value));
    }

    static int rgb(double red, double green, double blue) {
        return (clampChannel(red) << 16) | (clampChannel(green) << 8) | clampChannel(blue);
    }

    static int color(double red, double green, double blue, double alpha) {
        return (clampChannel(alpha) << 24) | rgb(red, green, blue);
    }

    static int clampChannel(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }
}
