package me.mrhakan.agalarhack.services;

/**
 * Speed and acceleration derived from where the player actually ended up.
 *
 * <p>Deliberately measured from position deltas rather than {@code getDeltaMovement}: the motion
 * vector is what the client intends, and it is zeroed by a collision the moment you scrape a wall,
 * so a speed HUD reading from it flickers exactly when the player is trying to judge whether
 * something is helping. Where the player moved to is the only figure that cannot disagree with what
 * they saw.
 *
 * <p>The client tick rate is fixed at {@value #TICKS_PER_SECOND} per second, which is a client fact
 * rather than anything about the server, so converting per-tick deltas to per-second here claims
 * nothing the client does not know.
 *
 * <p>Kept free of Minecraft types so the smoothing, discarding and acceleration rules are unit
 * tested directly.
 */
public final class MovementStats {
    public static final int TICKS_PER_SECOND = 20;
    /** Roughly one and a half seconds; long enough to read, short enough to follow a sprint jump. */
    public static final int WINDOW = 30;
    /**
     * Any single tick moving further than this is a teleport, a dimension change or a respawn, not
     * movement. Far above anything reachable — elytra with fireworks peaks near two blocks a tick.
     */
    public static final double MAX_PLAUSIBLE_BLOCKS_PER_TICK = 20.0;

    private final RollingSamples horizontal = new RollingSamples(WINDOW);
    private final RollingSamples vertical = new RollingSamples(WINDOW);

    private boolean hasPrevious;
    private double lastX;
    private double lastY;
    private double lastZ;
    private double previousHorizontalPerTick;
    private double accelerationPerTickSquared;

    /** Call once per client tick with the player's current position. */
    public void sample(double x, double y, double z) {
        if (!hasPrevious) {
            hasPrevious = true;
            lastX = x; lastY = y; lastZ = z;
            return;
        }
        double dx = x - lastX;
        double dy = y - lastY;
        double dz = z - lastZ;
        lastX = x; lastY = y; lastZ = z;

        double flat = Math.hypot(dx, dz);
        // A teleport would otherwise read as several hundred blocks a second and stay in the window.
        if (flat > MAX_PLAUSIBLE_BLOCKS_PER_TICK || Math.abs(dy) > MAX_PLAUSIBLE_BLOCKS_PER_TICK) {
            reset();
            hasPrevious = true;
            lastX = x; lastY = y; lastZ = z;
            return;
        }

        accelerationPerTickSquared = flat - previousHorizontalPerTick;
        previousHorizontalPerTick = flat;
        horizontal.add(flat);
        vertical.add(dy);
    }

    /**
     * The player leaves the world, or is replaced on respawn: every previous sample describes a
     * different situation and keeping them would smear across the gap.
     */
    public void reset() {
        horizontal.clear();
        vertical.clear();
        hasPrevious = false;
        previousHorizontalPerTick = 0;
        accelerationPerTickSquared = 0;
    }

    public boolean hasSamples() {
        return !horizontal.isEmpty();
    }

    /** Latest horizontal speed in blocks per second. */
    public double horizontalSpeed() {
        return horizontal.latest() * TICKS_PER_SECOND;
    }

    /** Latest vertical speed in blocks per second; negative while falling. */
    public double verticalSpeed() {
        return vertical.latest() * TICKS_PER_SECOND;
    }

    /** Smoothed horizontal speed over the window, which is the figure worth comparing runs with. */
    public double averageHorizontalSpeed() {
        return horizontal.average() * TICKS_PER_SECOND;
    }

    /** Fastest horizontal tick in the window, in blocks per second. */
    public double peakHorizontalSpeed() {
        return horizontal.maximum() * TICKS_PER_SECOND;
    }

    /** Fastest downward tick in the window, as a positive number; 0 when the player never fell. */
    public double peakFallSpeed() {
        double lowest = vertical.minimum();
        return lowest < 0 ? -lowest * TICKS_PER_SECOND : 0;
    }

    /**
     * Change in horizontal speed, in blocks per second per second.
     *
     * <p>From the last tick only rather than across the window: acceleration is what tells you
     * whether a speed change has already happened or is still happening, and averaging it away
     * defeats that.
     */
    public double horizontalAcceleration() {
        return accelerationPerTickSquared * TICKS_PER_SECOND * TICKS_PER_SECOND;
    }

    /** Recent horizontal speeds in blocks per second, oldest first, for a graph. */
    public double[] history() {
        double[] samples = horizontal.snapshot();
        for (int index = 0; index < samples.length; index++) samples[index] *= TICKS_PER_SECOND;
        return samples;
    }
}
