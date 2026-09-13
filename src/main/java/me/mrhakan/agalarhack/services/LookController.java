package me.mrhakan.agalarhack.services;

/**
 * Holds a deliberate aim open across ticks so a one-shot caller can use {@link RotationService}.
 *
 * <p>The rotation service expects a request every tick and drops anything that stops asking, which
 * is right for a module that re-decides each tick and wrong for a command typed once. This keeps
 * asking on that caller's behalf until the view arrives or the budget runs out, and then lets go.
 *
 * <p>This is the second consumer of the rotation service, and the first one that is not combat. It
 * moves the player's real view, visibly, through the same arbitration and the same per-tick step
 * limits as everything else — there is no hidden rotation here, and the roadmap rules that out.
 *
 * <p>Free of Minecraft types: the caller passes the view it has and applies the aim it gets back, so
 * the arrival test and the budget are unit tested directly.
 */
public final class LookController {
    /** Close enough to have arrived. Below about this the step limiter oscillates around the goal. */
    public static final float TOLERANCE_DEGREES = 0.5f;

    /** What to ask the rotation service for, minus the owner and priority the caller supplies. */
    public record Aim(float yaw, float pitch, boolean smooth, float speed,
                      float maxYawStep, float maxPitchStep) {
        public Aim {
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(speed) || speed <= 0
                    || !Float.isFinite(maxYawStep) || maxYawStep <= 0
                    || !Float.isFinite(maxPitchStep) || maxPitchStep <= 0) {
                throw new IllegalArgumentException("Invalid aim");
            }
        }
    }

    private Aim aim;
    private int remaining;

    /**
     * @param budgetTicks how long to keep trying before giving up, so a goal the step limit cannot
     *                    reach — or one interrupted by a screen — does not hold the view forever
     */
    public void aimAt(Aim wanted, int budgetTicks) {
        if (wanted == null || budgetTicks <= 0) { cancel(); return; }
        aim = wanted;
        remaining = budgetTicks;
    }

    public void cancel() {
        aim = null;
        remaining = 0;
    }

    public boolean active() { return aim != null; }

    /** Ticks remaining before this gives up; 0 when nothing is aimed. */
    public int remainingTicks() { return aim == null ? 0 : remaining; }

    /**
     * @return the aim to request this tick, or null when the view has arrived, the budget is spent,
     *         or nothing was aimed in the first place
     */
    public Aim tick(float currentYaw, float currentPitch) {
        if (aim == null) return null;
        if (arrived(currentYaw, currentPitch, aim.yaw(), aim.pitch())) { cancel(); return null; }
        if (--remaining <= 0) { cancel(); return null; }
        return aim;
    }

    /** True when the view is within tolerance of the goal, measured the short way round. */
    public static boolean arrived(float currentYaw, float currentPitch, float goalYaw, float goalPitch) {
        return Math.abs(TargetSelection.wrapDegrees(currentYaw - goalYaw)) <= TOLERANCE_DEGREES
                && Math.abs(currentPitch - goalPitch) <= TOLERANCE_DEGREES;
    }
}
