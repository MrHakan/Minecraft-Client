package me.mrhakan.agalarhack.services;

import me.mrhakan.agalarhack.module.Module;

/**
 * A colour that cycles through the hues over time.
 *
 * <p>Registered as a per-module setting rather than stored in the colour itself, because a colour is
 * a packed integer in the config and there is nowhere in it to record "this one moves". Keeping it a
 * separate flag also means the module's existing sliders stay meaningful: turning rainbow off gives
 * back exactly the colour that was there before.
 *
 * <p>Saturation and brightness come from the module's own colour rather than being fixed, so a muted
 * marker stays muted as it cycles instead of every rainbow in the client looking identical.
 *
 * <p>The phase offset is what makes a rainbow readable on a trail or a tracer: without it every
 * segment is the same colour at the same moment and the effect is just a flashing line.
 *
 * <p>Kept free of Minecraft types, and time is passed in rather than read, so the cycle is unit
 * tested directly.
 */
public final class RainbowColors {
    private RainbowColors() { }

    /** Adds the shared settings to a module that already has red/green/blue sliders. */
    public static void registerSettings(Module module) {
        module.settings.addBooleanSetting("rainbow", false, "Cycle this module's colour through the hues");
        module.settings.addNumberSetting("rainbowSeconds", 4.0, 0.5, 60.0, "Seconds for one full colour cycle");
    }

    /** True when the module is set to cycle; callers skip the rest of the work when it is not. */
    public static boolean enabled(Module module) {
        return module.getBooleanSetting("rainbow", false);
    }

    /**
     * @param millis a monotonic clock in milliseconds
     * @param baseRgb the module's configured colour, which supplies saturation and brightness
     * @param phase 0..1 offset along the cycle, so parts of one trail differ from each other
     * @return {@code 0xRRGGBB}
     */
    public static int cycle(long millis, double periodSeconds, int baseRgb, double phase) {
        double period = Math.max(0.1, periodSeconds) * 1000.0;
        // floorMod keeps a negative or pre-epoch clock on the cycle instead of off the end of it.
        double position = Math.floorMod(millis, (long) period) / period;
        float hue = (float) wrapUnit(position + phase);
        float[] hsb = toHsb(baseRgb);
        return hsbToRgb(hue, hsb[1], hsb[2]);
    }

    /** Convenience for the common case: a module's own colour, cycling at its own speed. */
    public static int cycle(Module module, long millis, int baseRgb, double phase) {
        return cycle(millis, module.getNumberSetting("rainbowSeconds", 4.0), baseRgb, phase);
    }

    private static double wrapUnit(double value) {
        double wrapped = value % 1.0;
        return wrapped < 0 ? wrapped + 1.0 : wrapped;
    }

    /**
     * A fully grey base has no hue to preserve, so it would cycle as grey and look broken. Those get
     * full saturation instead, which is what someone turning rainbow on was asking for.
     */
    static float[] toHsb(int rgb) {
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        if (hsb[1] < 0.05f) hsb[1] = 1.0f;
        if (hsb[2] < 0.15f) hsb[2] = 1.0f;
        return hsb;
    }

    static int hsbToRgb(float hue, float saturation, float brightness) {
        return java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF;
    }
}
