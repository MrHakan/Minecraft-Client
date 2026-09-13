package me.mrhakan.agalarhack.ui.hud;

import java.util.function.IntSupplier;

/** One evaluation per axis; invalid providers fail at the registry's isolation boundary. */
public record HudMeasurement(int width, int height) {
    public static HudMeasurement measure(IntSupplier width, IntSupplier height, int viewportWidth, int viewportHeight) {
        int w = width.getAsInt(), h = height.getAsInt();
        if (w < 0 || h < 0) throw new IllegalArgumentException("Negative HUD dimensions");
        return new HudMeasurement(Math.clamp(w, 1, Math.clamp(viewportWidth, 1, 4096)),
                Math.clamp(h, 1, Math.clamp(viewportHeight, 1, 4096)));
    }
}
