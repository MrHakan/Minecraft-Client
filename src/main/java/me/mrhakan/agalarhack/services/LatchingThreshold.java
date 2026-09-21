package me.mrhakan.agalarhack.services;

/**
 * Fires once when a value crosses into the alarming side, and re-arms only after it comes back.
 *
 * <p>Every warning in this client faces the same problem: the value hovers at the threshold, so a
 * naive check notifies on every tick it dips under and produces a wall of identical messages at
 * exactly the moment the player has something else to deal with. The release margin is what makes
 * that impossible — the value has to recover by a real amount, not by a rounding error, before the
 * warning can fire again.
 *
 * <p>Written once and shared because three detectors needed it. Kept free of Minecraft types so the
 * latching and re-arming are unit tested directly rather than inferred from a module's behaviour.
 */
public final class LatchingThreshold {
    public enum Direction {
        /** Alarming when the value falls below the threshold, as a tick-rate estimate does. */
        BELOW,
        /** Alarming when the value rises above it, as ping does. */
        ABOVE
    }

    private final Direction direction;
    private final double threshold;
    private final double releaseMargin;
    private boolean latched;

    /**
     * @param releaseMargin how far back past the threshold the value must come before re-arming;
     *                      negative values are treated as zero rather than inverting the logic
     */
    public LatchingThreshold(Direction direction, double threshold, double releaseMargin) {
        this.direction = direction;
        this.threshold = threshold;
        this.releaseMargin = Math.max(0, releaseMargin);
    }

    /** @return true on the tick the value first becomes alarming, and never again until it recovers */
    public boolean update(double value) {
        if (!Double.isFinite(value)) return false;
        boolean alarming = direction == Direction.BELOW ? value < threshold : value > threshold;
        if (alarming) {
            if (latched) return false;
            latched = true;
            return true;
        }
        boolean recovered = direction == Direction.BELOW
                ? value >= threshold + releaseMargin
                : value <= threshold - releaseMargin;
        if (recovered) latched = false;
        return false;
    }

    public boolean isLatched() {
        return latched;
    }

    /** Clears the latch without reporting anything; used when the situation itself has gone away. */
    public void reset() {
        latched = false;
    }
}
