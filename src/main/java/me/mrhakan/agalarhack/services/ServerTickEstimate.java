package me.mrhakan.agalarhack.services;

/**
 * Estimates server tick rate from the spacing of world-time updates.
 *
 * <p>This is an <em>estimate</em>, and every caller must present it as one. The client is never told
 * the server's tick rate; all it can observe is how far apart time updates arrive, which is also
 * affected by the network. A figure derived this way must never be presented as the server's
 * authoritative TPS.
 *
 * <p>Free of Minecraft types so the smoothing and the bounds are tested directly.
 */
public final class ServerTickEstimate {
    /** Vanilla's nominal rate; also the ceiling, since observing faster means the samples are noise. */
    public static final double NOMINAL_TPS = 20.0;

    private final RollingSamples intervals;
    private long lastUpdate = Long.MIN_VALUE;

    public ServerTickEstimate(int samples) {
        this.intervals = new RollingSamples(samples);
    }

    /**
     * Records a world-time update.
     *
     * @param now           monotonic milliseconds
     * @param ticksAdvanced how many server ticks the update represents; must be positive
     */
    public void update(long now, long ticksAdvanced) {
        if (ticksAdvanced <= 0) return;
        if (lastUpdate != Long.MIN_VALUE) {
            long elapsed = now - lastUpdate;
            // A non-positive or implausibly long gap is a pause or a clock problem, not a slow server.
            if (elapsed > 0 && elapsed < 60_000) {
                intervals.add(elapsed / (double) ticksAdvanced);
            }
        }
        lastUpdate = now;
    }

    public boolean hasEstimate() { return !intervals.isEmpty(); }

    /** Most recent estimate in ticks per second, clamped to a plausible range. */
    public double current() { return fromInterval(intervals.latest()); }

    /** Smoothed estimate over the retained samples; the figure worth showing. */
    public double average() { return fromInterval(intervals.average()); }

    /** Slowest observed rate, which comes from the longest interval. */
    public double minimum() { return fromInterval(intervals.maximum()); }

    /** Fastest observed rate, from the shortest interval. */
    public double maximum() { return fromInterval(intervals.minimum()); }

    public int samples() { return intervals.size(); }

    public void reset() {
        intervals.clear();
        lastUpdate = Long.MIN_VALUE;
    }

    private static double fromInterval(double millisecondsPerTick) {
        if (!Double.isFinite(millisecondsPerTick) || millisecondsPerTick <= 0) return 0;
        return Math.max(0, Math.min(NOMINAL_TPS, 1000.0 / millisecondsPerTick));
    }
}
