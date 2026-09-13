package me.mrhakan.agalarhack.services;

import java.util.Locale;

/**
 * Where a portal here would come out over there.
 *
 * <p>BlockESP can already highlight portal blocks, but the question players actually have is the one
 * highlighting cannot answer: standing in the overworld, where in the nether should I build so the
 * pair links, and vice versa. That is arithmetic the client can do exactly.
 *
 * <p>The ratio is vanilla's: eight overworld blocks to one nether block horizontally, and height
 * unchanged. The end is not scaled at all, which is why it is a separate case rather than a ratio of
 * one — saying "1:1" would suggest a link exists, and end portals do not pair by coordinate.
 *
 * <p>Free of Minecraft types so the rounding and the clamping are unit tested directly.
 */
public final class PortalMath {
    private PortalMath() { }

    public static final int NETHER_RATIO = 8;
    /** Matches the vanilla world border limit, which is also where a converted coordinate stops. */
    public static final int MAX_HORIZONTAL = 29_999_984;

    public static final String OVERWORLD = "minecraft:overworld";
    public static final String NETHER = "minecraft:the_nether";
    public static final String END = "minecraft:the_end";

    /** @param dimension the dimension the coordinates are in; x/z of the paired location */
    public record Link(String dimension, int x, int z) { }

    /**
     * @param dimension the dimension the player is standing in
     * @return the paired location, or null when the dimension does not pair by coordinate
     */
    public static Link pair(String dimension, int x, int z) {
        String id = dimension == null ? "" : dimension.toLowerCase(Locale.ROOT);
        if (OVERWORLD.equals(id)) return new Link(NETHER, divide(x), divide(z));
        if (NETHER.equals(id)) return new Link(OVERWORLD, clamp((long) x * NETHER_RATIO), clamp((long) z * NETHER_RATIO));
        // The end is reached by a fixed platform, not by a coordinate pairing; so is any modded
        // dimension we know nothing about. Saying nothing is the honest answer for both.
        return null;
    }

    /**
     * Overworld to nether.
     *
     * <p><strong>Floors.</strong> An earlier version divided with {@code /}, which truncates toward
     * zero, and said in this comment that integer division was what the game did. It is not:
     * {@code NetherPortalBlock} multiplies the position by the coordinate scale as a double and
     * passes it to {@code WorldBorder.clampToBounds(double, double, double)}, which is
     * {@code BlockPos.containing}, which is {@code Mth.floor}. Read from the 26.2 jar.
     *
     * <p>The two agree on zero and on positive coordinates and disagree on every negative one that
     * is not already a multiple of eight, so the suggestion was a block off in exactly the place the
     * old comment worried about - near an axis, which is where people build.
     */
    public static int divide(int value) {
        return clamp(Math.floorDiv(value, NETHER_RATIO));
    }

    private static int clamp(long value) {
        return (int) Math.max(-MAX_HORIZONTAL, Math.min(MAX_HORIZONTAL, value));
    }

    /**
     * How far away a linked portal may already be, in blocks of the target dimension.
     *
     * <p>Vanilla searches a radius around the converted position before building a new portal, so a
     * portal inside this distance links instead of a fresh one being created. Worth reporting: it is
     * the difference between "build exactly here" and "anywhere near here is fine".
     */
    public static int searchRadius(String targetDimension) {
        return NETHER.equals(targetDimension == null ? "" : targetDimension.toLowerCase(Locale.ROOT)) ? 16 : 128;
    }

    /** Short label for a chat line; unknown dimensions keep their id rather than being renamed. */
    public static String shortName(String dimension) {
        String id = dimension == null ? "" : dimension.toLowerCase(Locale.ROOT);
        return switch (id) {
            case OVERWORLD -> "overworld";
            case NETHER -> "nether";
            case END -> "end";
            default -> dimension == null ? "unknown" : dimension;
        };
    }
}
