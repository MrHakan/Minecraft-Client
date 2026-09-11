package me.mrhakan.agalarhack.services;

import java.util.Locale;

/**
 * A saved position.
 *
 * <p>Values are normalised in the canonical constructor, so a waypoint that exists is always
 * renderable: the name is trimmed and bounded, coordinates are finite and inside Minecraft's world
 * limits, and the colour is opaque. Callers therefore never have to re-validate one they were given.
 */
public record Waypoint(String name, int x, int y, int z, String dimension, int color,
                       boolean visible, boolean beam) {
    public static final int MAX_NAME = 32;
    /** Matches the vanilla world border limit; well beyond anywhere a player can stand. */
    public static final int MAX_HORIZONTAL = 30_000_000;
    public static final int MIN_Y = -2048;
    public static final int MAX_Y = 2048;
    public static final String DEFAULT_DIMENSION = "minecraft:overworld";
    public static final int DEFAULT_COLOR = 0xFF55FFFF;

    public Waypoint {
        name = normaliseName(name);
        dimension = normaliseDimension(dimension);
        x = clamp(x, -MAX_HORIZONTAL, MAX_HORIZONTAL);
        z = clamp(z, -MAX_HORIZONTAL, MAX_HORIZONTAL);
        y = clamp(y, MIN_Y, MAX_Y);
        color = 0xFF000000 | (color & 0x00FFFFFF);
    }

    public static Waypoint of(String name, int x, int y, int z, String dimension) {
        return new Waypoint(name, x, y, z, dimension, DEFAULT_COLOR, true, false);
    }

    /** Case-insensitive identity: two waypoints with the same name in the same dimension are one. */
    public String key() {
        return dimension + "/" + name.toLowerCase(Locale.ROOT);
    }

    public Waypoint withColor(int color) {
        return new Waypoint(name, x, y, z, dimension, color, visible, beam);
    }

    public Waypoint withVisible(boolean visible) {
        return new Waypoint(name, x, y, z, dimension, color, visible, beam);
    }

    public Waypoint withBeam(boolean beam) {
        return new Waypoint(name, x, y, z, dimension, color, visible, beam);
    }

    /** Horizontal distance; vertical separation is ignored so a deep base still reads sensibly. */
    public double horizontalDistanceTo(double px, double pz) {
        double dx = x + 0.5 - px;
        double dz = z + 0.5 - pz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String normaliseName(String raw) {
        String trimmed = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Waypoint name must not be empty");
        return trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME) : trimmed;
    }

    private static String normaliseDimension(String raw) {
        String id = ItemIdList.canonical(raw);
        return id == null ? DEFAULT_DIMENSION : id;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
