package me.mrhakan.agalarhack.services.projectile;

/**
 * Per-tick motion of a thrown or fired projectile.
 *
 * <p>Minecraft applies drag and gravity in a different order for arrow-like and throwable-like
 * projectiles, and getting that backwards produces a path that looks plausible but lands in the
 * wrong place. The order is therefore an explicit part of the family rather than an assumption.
 *
 * <p>Free of Minecraft types so the families and the integration can be tested directly. The values
 * are carried over unchanged from the previously verified in-renderer simulation.
 */
public record ProjectilePhysics(double speed, double gravity, double drag, double pitchOffset,
                                boolean gravityBeforeDrag) {

    public ProjectilePhysics {
        speed = finite(speed, 1.5);
        gravity = Math.max(0, finite(gravity, 0.03));
        drag = Math.max(0.0, Math.min(1.0, finite(drag, 0.99)));
        pitchOffset = finite(pitchOffset, 0);
    }

    /** The projectile groups the client can simulate. */
    public enum Family {
        /** Arrows from a bow, including the drawn-power case handled by the caller. */
        BOW,
        CROSSBOW,
        TRIDENT,
        /** Snowballs, eggs and ender pearls all share the same throwable motion. */
        THROWN,
        SPLASH_POTION,
        LINGERING_POTION,
        XP_BOTTLE
    }

    private static final double ARROW_GRAVITY = 0.05;
    private static final double AIR_DRAG = 0.99;

    /**
     * @param speed the launch speed for families whose speed depends on charge; ignored otherwise
     */
    public static ProjectilePhysics of(Family family, double speed) {
        return switch (family) {
            case BOW -> new ProjectilePhysics(speed, ARROW_GRAVITY, AIR_DRAG, 0, false);
            case CROSSBOW -> new ProjectilePhysics(3.15, ARROW_GRAVITY, AIR_DRAG, 0, false);
            case TRIDENT -> new ProjectilePhysics(2.5, ARROW_GRAVITY, AIR_DRAG, 0, false);
            case THROWN -> new ProjectilePhysics(1.5, 0.03, AIR_DRAG, 0, true);
            case SPLASH_POTION, LINGERING_POTION -> new ProjectilePhysics(0.5, 0.05, AIR_DRAG, -20.0, true);
            case XP_BOTTLE -> new ProjectilePhysics(0.7, 0.07, AIR_DRAG, -20.0, true);
        };
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
