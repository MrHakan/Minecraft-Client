package me.mrhakan.agalarhack.services;

/**
 * Keeps text readable against whatever background a theme ends up with.
 *
 * <p>The high-contrast switch is all-or-nothing: black and white, and nothing else. That leaves the
 * common case unhandled — a player picks a background in the colour picker, or imports someone's
 * theme, and the muted text becomes almost invisible against it. Nobody turns high contrast on for
 * that; they just squint, or think the client is broken.
 *
 * <p>Uses the WCAG relative-luminance formula rather than a naive brightness average, because the
 * naive version says yellow on white is fine and blue on black is not, which is backwards for both.
 *
 * <p>Free of Minecraft types so the formula and the search are unit tested against known values.
 */
public final class ContrastRules {
    private ContrastRules() { }

    /** WCAG AA for body text. Below this, text is hard to read for a lot of people, not a few. */
    public static final double MINIMUM_RATIO = 4.5;

    /** WCAG AA for large text, which is what a heading or an accent stripe is. */
    public static final double LARGE_TEXT_RATIO = 3.0;

    /** @return 0 for black, 1 for white; alpha is ignored because it is not part of the colour */
    public static double relativeLuminance(int rgb) {
        double red = channel((rgb >> 16) & 0xFF);
        double green = channel((rgb >> 8) & 0xFF);
        double blue = channel(rgb & 0xFF);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double channel(int value) {
        double normalised = value / 255.0;
        return normalised <= 0.04045 ? normalised / 12.92 : Math.pow((normalised + 0.055) / 1.055, 2.4);
    }

    /** @return between 1 (identical) and 21 (black against white); order of the arguments does not matter */
    public static double contrastRatio(int first, int second) {
        double a = relativeLuminance(first);
        double b = relativeLuminance(second);
        double lighter = Math.max(a, b);
        double darker = Math.min(a, b);
        return (lighter + 0.05) / (darker + 0.05);
    }

    public static boolean isReadable(int foreground, int background, double minimumRatio) {
        return contrastRatio(foreground, background) >= minimumRatio;
    }

    /**
     * Moves the foreground toward black or white until it meets the ratio.
     *
     * <p>Toward whichever end the background is furthest from, so the result stays as close to the
     * colour the player chose as the requirement allows — a dark theme keeps a recognisably tinted
     * light text rather than being flattened to pure white.
     *
     * <p>Returns the nearest end outright if even that does not reach the ratio, which happens when
     * the background sits in the middle of the range: mid-grey has a maximum contrast of about 5.3,
     * so some requirements are simply unreachable and pretending otherwise would loop.
     *
     * @return {@code 0xAARRGGBB} with the foreground's original alpha preserved
     */
    public static int ensureReadable(int foreground, int background, double minimumRatio) {
        int alpha = foreground & 0xFF000000;
        int rgb = foreground & 0x00FFFFFF;
        if (isReadable(rgb, background, minimumRatio)) return foreground;

        // Blend toward the end that is further from the background in luminance terms.
        int target = relativeLuminance(background) > 0.5 ? 0x000000 : 0xFFFFFF;
        int best = target;
        for (int step = 1; step <= 20; step++) {
            int candidate = blend(rgb, target, step / 20.0);
            if (isReadable(candidate, background, minimumRatio)) {
                best = candidate;
                break;
            }
        }
        return alpha | (best & 0x00FFFFFF);
    }

    /** @param amount 0 keeps {@code from}, 1 returns {@code to} */
    public static int blend(int from, int to, double amount) {
        double clamped = Math.max(0, Math.min(1, amount));
        int red = mix((from >> 16) & 0xFF, (to >> 16) & 0xFF, clamped);
        int green = mix((from >> 8) & 0xFF, (to >> 8) & 0xFF, clamped);
        int blue = mix(from & 0xFF, to & 0xFF, clamped);
        return (red << 16) | (green << 8) | blue;
    }

    private static int mix(int from, int to, double amount) {
        return (int) Math.round(from + (to - from) * amount);
    }
}
