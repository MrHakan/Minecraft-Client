package me.mrhakan.agalarhack.services.projectile;

/**
 * Reusable projectile path stepping.
 *
 * <p>Extracted from the trajectory renderer so the trajectory overlay, projectile highlighting and
 * incoming-projectile warnings all predict motion the same way instead of each carrying a copy.
 * Collision is deliberately not handled here: it needs the world, and keeping it out is what makes
 * the physics testable.
 */
public final class ProjectileSimulator {
    private ProjectileSimulator() { }

    /** A position and velocity in blocks and blocks-per-tick. */
    public record State(double x, double y, double z, double vx, double vy, double vz) { }

    /**
     * Initial state for a launch from {@code (x, y, z)} along the given view angles.
     *
     * @param inheritX,inheritY,inheritZ motion inherited from the shooter; pass zeroes to ignore it
     */
    public static State launch(double x, double y, double z, float yawDegrees, float pitchDegrees,
            ProjectilePhysics physics, double power,
            double inheritX, double inheritY, double inheritZ) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double offsetPitch = Math.toRadians(pitchDegrees + physics.pitchOffset());
        double dx = -Math.sin(yaw) * Math.cos(pitch);
        double dy = -Math.sin(offsetPitch);
        double dz = Math.cos(yaw) * Math.cos(pitch);
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 1.0E-7) { dx /= length; dy /= length; dz /= length; }
        double scale = physics.speed() * (Double.isFinite(power) ? power : 1.0);
        return new State(x, y, z,
                dx * scale + inheritX, dy * scale + inheritY, dz * scale + inheritZ);
    }

    /**
     * Advances one tick.
     *
     * <p>Arrow-like projectiles scale by drag and then subtract gravity; throwable-like ones subtract
     * gravity first. Both orders move by the velocity that results, matching vanilla.
     */
    public static State advance(State state, ProjectilePhysics physics) {
        double vx = state.vx();
        double vy = state.vy();
        double vz = state.vz();
        if (physics.gravityBeforeDrag()) {
            vy -= physics.gravity();
            vx *= physics.drag();
            vy *= physics.drag();
            vz *= physics.drag();
            return new State(state.x() + vx, state.y() + vy, state.z() + vz, vx, vy, vz);
        }
        double nextX = state.x() + vx;
        double nextY = state.y() + vy;
        double nextZ = state.z() + vz;
        vx *= physics.drag();
        vy = vy * physics.drag() - physics.gravity();
        vz *= physics.drag();
        return new State(nextX, nextY, nextZ, vx, vy, vz);
    }

    /** Horizontal speed in blocks per tick, useful for labelling an observed projectile. */
    public static double horizontalSpeed(State state) {
        return Math.hypot(state.vx(), state.vz());
    }

    /**
     * Closest approach of a projectile to a point over the next {@code steps} ticks, ignoring
     * collision. Used by the incoming-projectile warning, which is informational only.
     */
    public record Approach(int ticks, double distance) { }

    public static Approach closestApproach(State state, ProjectilePhysics physics,
            double targetX, double targetY, double targetZ, int steps) {
        int bounded = Math.max(1, Math.min(400, steps));
        State current = state;
        double best = distance(current, targetX, targetY, targetZ);
        int bestTick = 0;
        for (int tick = 1; tick <= bounded; tick++) {
            current = advance(current, physics);
            double distance = distance(current, targetX, targetY, targetZ);
            if (distance < best) { best = distance; bestTick = tick; }
        }
        return new Approach(bestTick, best);
    }

    private static double distance(State state, double x, double y, double z) {
        double dx = state.x() - x;
        double dy = state.y() - y;
        double dz = state.z() - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
