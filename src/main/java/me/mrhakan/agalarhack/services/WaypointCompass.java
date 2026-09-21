package me.mrhakan.agalarhack.services;

/**
 * Direction from the player to a waypoint, as an angle relative to where they are looking.
 *
 * <p>Minecraft's yaw convention is easy to get backwards, so the maths lives here and is tested
 * directly: yaw 0 faces +Z, and yaw increases turning left, which means a target to the player's
 * right has a positive relative bearing.
 */
public final class WaypointCompass {
    private WaypointCompass() { }

    /** Arrows in bearing order starting straight ahead and going clockwise (to the player's right). */
    private static final char[] ARROWS = { '↑', '↗', '→', '↘', '↓', '↙', '←', '↖' };

    /**
     * @return degrees in -180..180; negative is to the player's left, positive to their right,
     *         and 0 is straight ahead
     */
    public static double relativeBearing(double playerX, double playerZ, double targetX, double targetZ, float yaw) {
        double dx = targetX - playerX;
        double dz = targetZ - playerZ;
        if (dx == 0 && dz == 0) return 0;
        double required = Math.toDegrees(Math.atan2(-dx, dz));
        return wrapDegrees(required - yaw);
    }

    /** Normalises any angle into -180..180 so callers never compare across the wrap point. */
    public static double wrapDegrees(double degrees) {
        if (!Double.isFinite(degrees)) return 0;
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    /** Eight-way arrow for a relative bearing. */
    public static char arrow(double relativeBearing) {
        double wrapped = wrapDegrees(relativeBearing);
        // Shift by half a sector so each arrow covers the 45 degrees centred on its own direction.
        int sector = (int) Math.floor((wrapped + 180.0 + 22.5) / 45.0) % 8;
        // +180 above put "behind" at index 0, so rotate back to make "ahead" index 0.
        return ARROWS[(sector + 4) % 8];
    }
}
