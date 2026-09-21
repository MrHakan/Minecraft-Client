package me.mrhakan.agalarhack.services;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Client text lookup with an English fallback baked into every call.
 *
 * <p>Localisation here is deliberately incremental, so the fallback is the whole design: a key that
 * has no translation renders the English text passed at the call site, never a raw
 * {@code agalarhack.gui.search} placeholder. That makes it safe to translate one screen at a time
 * without any point in between where the client looks broken.
 *
 * <p>Keys are namespaced {@code agalarhack.<area>.<name>}. Only add a key here when the English text
 * is also added to {@code en_us.json}; the fallback covers the gap, but a key with no English entry
 * is a bug waiting for a translator to expose it.
 */
public final class Translations {
    private Translations() { }

    public static final String PREFIX = "agalarhack.";

    /** A translated component, falling back to {@code english} when the key is missing. */
    public static MutableComponent text(String key, String english) {
        return Component.translatableWithFallback(qualify(key), english);
    }

    public static MutableComponent text(String key, String english, Object... args) {
        return Component.translatableWithFallback(qualify(key), english, args);
    }

    /** Plain string form, for the places that build labels rather than components. */
    public static String string(String key, String english) {
        return text(key, english).getString();
    }

    private static String qualify(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Translation key must not be blank");
        String trimmed = key.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith(PREFIX) ? trimmed : PREFIX + trimmed;
    }

    /** Exposed for tests and tooling: the fully qualified key a call site would use. */
    public static String keyOf(String key) { return qualify(key); }
}
