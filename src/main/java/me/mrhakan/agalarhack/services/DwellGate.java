package me.mrhakan.agalarhack.services;

/**
 * Counts how long attention has rested on one thing.
 *
 * <p>A crosshair-driven module fires the instant the crosshair crosses anything valid, which means
 * turning past a passive mob attacks it. Requiring the crosshair to stay on a target for a moment
 * makes that impossible without limiting what the player can deliberately aim at.
 *
 * <p>The count is deliberately plain and deterministic: a fixed number of ticks, not a randomised
 * one. Randomising it would serve only to imitate human jitter, which is a different goal from the
 * one this solves.
 *
 * <p>Works on ids rather than entities so the counting and reset rules are unit tested directly.
 */
public final class DwellGate {
    /** No target; passing this resets the count, so a gap in attention starts over. */
    public static final int NONE = -1;

    private int current = NONE;
    private int ticks;

    /**
     * Call once per tick with whatever attention is currently on.
     *
     * @param targetId the thing being looked at, or {@link #NONE}
     * @param requiredTicks how long it must have been held; 0 or less is always ready
     * @return true when the target has been held long enough
     */
    public boolean ready(int targetId, int requiredTicks) {
        if (targetId == NONE) {
            reset();
            return false;
        }
        if (targetId != current) {
            current = targetId;
            ticks = 0;
        }
        // Saturates rather than wrapping: a target held for hours must not suddenly read as new.
        if (ticks < Integer.MAX_VALUE) ticks++;
        return ticks >= Math.max(1, requiredTicks);
    }

    public int ticksOnTarget() {
        return ticks;
    }

    public int current() {
        return current;
    }

    public void reset() {
        current = NONE;
        ticks = 0;
    }
}
