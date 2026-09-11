package me.mrhakan.agalarhack.services;

/**
 * Decides which of several valid targets to actually hit.
 *
 * <p>Priority ordering alone is not enough once two targets are nearly equal: "closest" flips between
 * them as they move, and every flip throws away the attack charge that was building, so the aura hits
 * less than it would have by picking one and staying. A switch delay fixes that — a better target has
 * to stay better for a moment before the switch is worth paying for.
 *
 * <p>Losing a target is different from being offered a better one, and the delay deliberately does
 * not apply there: if the current target died or walked out of range there is nothing to hold on to,
 * so the next one is taken immediately.
 *
 * <p>Round-robin across several targets is the other half. It advances on landed attacks rather than
 * on ticks, so each target in the rotation gets a real hit instead of the rotation running ahead of
 * the attack timing and spraying.
 *
 * <p>Works on entity ids rather than entities so the rules are unit tested directly.
 */
public final class TargetRotation {
    /** Returned when nothing should be attacked; no real entity has this id. */
    public static final int NONE = -1;

    private final int switchDelayTicks;
    private int current = NONE;
    private long lastSwitchTick = Long.MIN_VALUE;
    private int rotationIndex;

    /** @param switchDelayTicks how long a held target survives a better candidate; 0 disables holding */
    public TargetRotation(int switchDelayTicks) {
        this.switchDelayTicks = Math.max(0, switchDelayTicks);
    }

    /**
     * @param candidates valid targets, already ordered by the module's priority setting
     * @param maxTargets how many to spread attacks across; 1 is the ordinary single-target behaviour
     * @return the id to attack this tick, or {@link #NONE}
     */
    public int select(long tick, int[] candidates, int maxTargets) {
        if (candidates == null || candidates.length == 0) {
            reset();
            return NONE;
        }
        int limit = Math.max(1, Math.min(maxTargets, candidates.length));
        if (limit > 1) {
            // The index is bounded by the modulo, so a shrinking candidate list cannot index past it.
            current = candidates[Math.floorMod(rotationIndex, limit)];
            return current;
        }

        boolean stillValid = contains(candidates, current);
        if (stillValid && candidates[0] != current && tick - lastSwitchTick < switchDelayTicks) {
            return current;
        }
        if (!stillValid || candidates[0] != current) {
            current = candidates[0];
            lastSwitchTick = tick;
        }
        return current;
    }

    /** Called after an attack actually lands, which is what advances a multi-target rotation. */
    public void attacked(long tick) {
        lastSwitchTick = tick;
        // Wraps rather than growing without bound over a long session.
        rotationIndex = rotationIndex == Integer.MAX_VALUE ? 0 : rotationIndex + 1;
    }

    public int current() {
        return current;
    }

    public void reset() {
        current = NONE;
        lastSwitchTick = Long.MIN_VALUE;
        rotationIndex = 0;
    }

    private static boolean contains(int[] values, int wanted) {
        if (wanted == NONE) return false;
        for (int value : values) {
            if (value == wanted) return true;
        }
        return false;
    }
}
