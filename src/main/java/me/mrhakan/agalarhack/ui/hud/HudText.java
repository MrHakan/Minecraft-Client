package me.mrhakan.agalarhack.ui.hud;

import java.util.Objects;
import java.util.function.ToIntFunction;

/** Small text-fitting helpers shared by HUD components. */
public final class HudText {
    @FunctionalInterface
    public interface PrefixByWidth {
        String apply(String text, int maximumWidth);
    }

    private static final String ELLIPSIS = "…";

    private HudText() { }

    /**
     * Returns the original text when it fits, otherwise uses the caller's width-aware
     * substring operation once and appends an ellipsis if that marker fits.
     */
    public static String fitWithEllipsis(String text, int maximumWidth, ToIntFunction<String> measure,
                                         PrefixByWidth prefixByWidth) {
        if (text == null || text.isEmpty() || maximumWidth <= 0) return "";
        Objects.requireNonNull(measure, "measure");
        Objects.requireNonNull(prefixByWidth, "prefixByWidth");
        if (measure.applyAsInt(text) <= maximumWidth) return text;

        int ellipsisWidth = measure.applyAsInt(ELLIPSIS);
        if (ellipsisWidth > maximumWidth) return "";
        String prefix = prefixByWidth.apply(text, Math.max(0, maximumWidth - ellipsisWidth));
        if (prefix == null) prefix = "";
        String candidate = prefix + ELLIPSIS;
        return measure.applyAsInt(candidate) <= maximumWidth ? candidate : ELLIPSIS;
    }
}
