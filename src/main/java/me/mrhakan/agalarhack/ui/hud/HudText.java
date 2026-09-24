package me.mrhakan.agalarhack.ui.hud;

import java.util.Objects;
import java.util.function.ToIntFunction;

/** Small text-fitting helpers shared by HUD components. */
public final class HudText {
    private static final String ELLIPSIS = "…";

    private HudText() { }

    /**
     * Returns the original text when it fits, otherwise trims whole Unicode code points and
     * appends an ellipsis when that marker fits the available width.
     */
    public static String fitWithEllipsis(String text, int maximumWidth, ToIntFunction<String> measure) {
        if (text == null || text.isEmpty() || maximumWidth <= 0) return "";
        Objects.requireNonNull(measure, "measure");
        if (measure.applyAsInt(text) <= maximumWidth) return text;

        int ellipsisWidth = measure.applyAsInt(ELLIPSIS);
        if (ellipsisWidth > maximumWidth) return "";

        int end = text.length();
        while (end > 0) {
            int previous = text.offsetByCodePoints(end, -1);
            String candidate = text.substring(0, previous) + ELLIPSIS;
            if (measure.applyAsInt(candidate) <= maximumWidth) return candidate;
            end = previous;
        }
        return ELLIPSIS;
    }
}
