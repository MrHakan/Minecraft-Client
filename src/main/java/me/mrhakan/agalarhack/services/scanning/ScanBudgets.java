package me.mrhakan.agalarhack.services.scanning;

import java.util.Locale;

/**
 * The shared per-tick ceiling every scanner draws from.
 *
 * <p>The ceiling is what keeps scanning bounded: modules ask for what they want, and this is the
 * total they can actually be given in one client tick. One number for every machine is a compromise
 * that suits neither end — a low ceiling makes ESP results crawl in on a machine that had headroom,
 * and a high one costs frames on a machine that did not.
 *
 * <p>The profiles scale all three limits together on purpose. They are not independent: a block probe
 * usually needs a chunk lookup, so raising one without the other just moves where the scan stalls.
 *
 * <p>Kept free of Minecraft types so the profile names and the clamping are unit tested directly.
 */
public record ScanBudgets(int blocks, int chunkLookups, int entities) {
    /** What the client used before this was configurable; "balanced" reproduces it exactly. */
    public static final ScanBudgets BALANCED = new ScanBudgets(12_000, 64, 4096);

    /** Upper bound on any single figure, so a hand-edited config cannot stall the client tick. */
    public static final int MAX = 64_000;

    public ScanBudgets {
        blocks = clamp(blocks);
        chunkLookups = clamp(chunkLookups);
        entities = clamp(entities);
    }

    /**
     * @param profile one of {@code low}, {@code balanced}, {@code high}; anything else is balanced,
     *                because an unrecognised profile should behave like the default rather than
     *                silently stop scanning
     */
    public static ScanBudgets forProfile(String profile) {
        return switch (profile == null ? "" : profile.toLowerCase(Locale.ROOT)) {
            case "low" -> new ScanBudgets(4_000, 24, 1_536);
            case "high" -> new ScanBudgets(28_000, 128, 8_192);
            default -> BALANCED;
        };
    }

    private static int clamp(int value) {
        return Math.max(1, Math.min(MAX, value));
    }
}
