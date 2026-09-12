package me.mrhakan.agalarhack.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import me.mrhakan.agalarhack.services.ThemeService.Theme;

/** Validates external themes before Gson can coerce strings or truncate integer values. */
public final class ThemeCodec {
    public static final int MAX_BYTES = 16384;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> INTEGERS = Set.of("schemaVersion", "background", "panel", "accent",
            "text", "muted", "on", "off", "cornerRadius");
    private static final Set<String> NUMBERS = Set.of("panelOpacity", "animationSpeed", "hudScale");

    private static final Set<String> BOOLEANS = Set.of("shadows", "uiAnimations", "reducedMotion", "highContrast",
            "enforceContrast");

    private ThemeCodec() { }

    public static Theme decode(String raw) {
        if (raw == null || raw.length() > MAX_BYTES || raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Theme exceeds 16 KiB");
        var root = JsonParser.parseString(raw);
        if (!root.isJsonObject()) throw new IllegalArgumentException("Theme must be an object");
        for (var entry : root.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            if (!entry.getValue().isJsonPrimitive()) throw new IllegalArgumentException("Invalid theme field: " + key);
            var value = entry.getValue().getAsJsonPrimitive();
            if (INTEGERS.contains(key)) {
                if (!value.isNumber()) throw new IllegalArgumentException("Expected integer: " + key);
                try { value.getAsBigDecimal().intValueExact(); }
                catch (ArithmeticException failure) { throw new IllegalArgumentException("Invalid integer: " + key); }
            } else if (NUMBERS.contains(key)) {
                if (!value.isNumber() || !Double.isFinite(value.getAsDouble()))
                    throw new IllegalArgumentException("Expected finite number: " + key);
            } else if (BOOLEANS.contains(key)) {
                if (!value.isBoolean()) throw new IllegalArgumentException("Expected boolean: " + key);
            } else if (!key.equals("name") || !value.isString()) {
                throw new IllegalArgumentException("Unsupported theme field: " + key);
            }
        }
        Theme theme = GSON.fromJson(root, Theme.class);
        if (theme.schemaVersion != 1) throw new IllegalArgumentException("Unsupported theme schema");
        theme.panelOpacity = Math.clamp(theme.panelOpacity, 0.2, 1);
        theme.animationSpeed = Math.clamp(theme.animationSpeed, 0.25, 4);
        theme.cornerRadius = Math.clamp(theme.cornerRadius, 0, 12);
        theme.hudScale = me.mrhakan.agalarhack.services.HudScale.clamp(theme.hudScale);
        if (theme.name.length() > 40) theme.name = theme.name.substring(0, 40);
        return theme;
    }

    public static String encode(Theme theme) { return GSON.toJson(decode(GSON.toJson(theme))); }
    public static Theme copy(Theme theme) { return decode(encode(theme)); }
}
