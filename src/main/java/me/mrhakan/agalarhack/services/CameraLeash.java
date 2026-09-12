package me.mrhakan.agalarhack.services;

/**
 * Holds a detached camera within a radius of the body it left behind.
 *
 * <p>Without a limit a freecam is a scouting tool: fly it a hundred blocks out and read a base you
 * could not otherwise see. A radius makes it what it is meant to be — a way to look at yourself and
 * your surroundings — and the default is deliberately short enough to be that rather than a
 * substitute for walking there. Zero means no limit, for anyone who wants the old behaviour back.
 *
 * <p>The clamp pulls the camera back along the line to the anchor rather than stopping it dead, so a
 * camera already outside the radius when the setting changes eases in instead of snapping.
 *
 * <p>Free of Minecraft types so the geometry is unit tested directly.
 */
public final class CameraLeash {
    /** Far enough to see yourself and the ground you are standing on, not far enough to scout. */
    public static final double DEFAULT_DISTANCE = 24.0;
    public static final double MAXIMUM_DISTANCE = 128.0;

    private CameraLeash() { }

    public record Point(double x, double y, double z) { }

    /**
     * @param maxDistance radius in blocks; zero or less leaves the position untouched
     * @return the position, moved back onto the radius if it was outside it
     */
    public static Point clamp(Point anchor, Point camera, double maxDistance) {
        if (anchor == null || camera == null) return camera;
        if (!(maxDistance > 0) || !Double.isFinite(maxDistance)) return camera;
        double dx = camera.x() - anchor.x();
        double dy = camera.y() - anchor.y();
        double dz = camera.z() - anchor.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(distance) || distance <= maxDistance) return camera;
        // distance is strictly greater than a positive maxDistance here, so it cannot be zero.
        double scale = maxDistance / distance;
        return new Point(anchor.x() + dx * scale, anchor.y() + dy * scale, anchor.z() + dz * scale);
    }

    /** Straight-line distance, for reporting how far out the camera is. */
    public static double distance(Point anchor, Point camera) {
        if (anchor == null || camera == null) return 0;
        double dx = camera.x() - anchor.x();
        double dy = camera.y() - anchor.y();
        double dz = camera.z() - anchor.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
