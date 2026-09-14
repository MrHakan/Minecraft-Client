package me.mrhakan.agalarhack.services;

/**
 * Eased progress for a one-shot screen transition.
 *
 * <p>Only decoration is animated with this — a selection stripe sliding to the category you picked,
 * a panel fading in. Nothing interactive moves, on purpose: the ClickGUI is built from vanilla
 * widgets that hit-test against their own bounds, so a button drawn somewhere other than where it
 * answers clicks is a worse bug than a screen that appears instantly.
 *
 * <p>Reduced motion is not a special case bolted on top; it is the same code path with a duration of
 * zero, so a themed client with motion off gets the settled frame and never an intermediate one.
 *
 * <p>Free of Minecraft types so the easing and the clamping are unit tested directly.
 */
public final class ScreenTransition {
    /** Long enough to read as movement, short enough that nobody waits for it. */
    public static final long DEFAULT_MILLIS = 180;

    private ScreenTransition() { }

    /**
     * Linear progress through the transition.
     *
     * @param elapsedMillis time since the transition started; negative is treated as not started
     * @param durationMillis total length; zero or less finishes immediately
     * @return 0 at the start, 1 once finished
     */
    public static double progress(long elapsedMillis, long durationMillis) {
        if (durationMillis <= 0) return 1;
        if (elapsedMillis <= 0) return 0;
        return Math.min(1, elapsedMillis / (double) durationMillis);
    }

    /**
     * How long a transition should last for a theme.
     *
     * @param motionEnabled the theme's own switch; false means no animation at all
     * @param animationSpeed the theme's speed multiplier, where larger is faster
     */
    public static long duration(boolean motionEnabled, double animationSpeed) {
        if (!motionEnabled) return 0;
        double speed = Double.isFinite(animationSpeed) ? Math.clamp(animationSpeed, 0.25, 4) : 1;
        return Math.max(1, Math.round(DEFAULT_MILLIS / speed));
    }

    /**
     * Ease-out cubic: quick to leave, gentle to arrive.
     *
     * <p>Chosen over a linear ramp because the thing being animated is arriving somewhere the eye is
     * already looking, and a linear stop reads as a jolt at that size.
     */
    public static double ease(double progress) {
        double clamped = Double.isFinite(progress) ? Math.clamp(progress, 0, 1) : 1;
        double remaining = 1 - clamped;
        return 1 - remaining * remaining * remaining;
    }

    /** Interpolates between two positions with the eased progress. */
    public static double between(double from, double to, double progress) {
        return from + (to - from) * ease(progress);
    }
}
