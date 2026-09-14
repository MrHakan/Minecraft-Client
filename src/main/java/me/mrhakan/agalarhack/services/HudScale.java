package me.mrhakan.agalarhack.services;

/**
 * One logical-to-physical mapping for the HUD overlay.
 *
 * <p>The mod draws its own HUD, so how large it reads is the mod's to decide; the screens are not,
 * because Minecraft's own GUI scale already scales those and a second control beside it would be a
 * switch that forwards to a vanilla switch.
 *
 * <p>Three places have to agree about that mapping or the HUD ends up drawn somewhere other than
 * where the editor says it is: the render pass that scales the matrix, the layout that decides where
 * a right-anchored widget sits, and the editor that drags one around. They all come here rather than
 * each dividing by the scale in their own way.
 *
 * <p>Kept free of Minecraft types so the arithmetic is unit tested directly.
 */
public final class HudScale {
    /** Half size is still legible at a large GUI scale; double is about as far as anchoring stays useful. */
    public static final double MINIMUM = 0.5;
    public static final double MAXIMUM = 2.0;
    public static final double DEFAULT = 1.0;

    private HudScale() { }

    /** A usable scale from any stored value, including one a hand-edited file made nonsense. */
    public static double clamp(double scale) {
        return Double.isFinite(scale) ? Math.clamp(scale, MINIMUM, MAXIMUM) : DEFAULT;
    }

    /**
     * The width or height the HUD lays itself out in.
     *
     * <p>Drawing is scaled by the matrix, so a widget anchored to the right edge must be placed
     * against the <em>logical</em> edge — the physical one divided by the scale — or it lands off
     * screen at any scale above one.
     *
     * <p>Floored rather than rounded, and never below one: a widget placed at the rounded-up edge of
     * a screen that is a fraction smaller would sit just past it, and a zero-width screen would make
     * every clamp collapse to nothing.
     */
    public static int logical(int physical, double scale) {
        return Math.max(1, (int) Math.floor(Math.max(0, physical) / clamp(scale)));
    }

    /** A mouse position in the same logical space the widgets are laid out in. */
    public static double toLogical(double physical, double scale) {
        return physical / clamp(scale);
    }
}
