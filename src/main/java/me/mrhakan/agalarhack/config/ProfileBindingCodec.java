package me.mrhakan.agalarhack.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * Bounded codec for the legacy profile-binding map files.
 *
 * <p>The files intentionally remain plain JSON objects for backwards compatibility. Keeping the
 * validation here makes both server and dimension bindings obey the same size and type rules before
 * a caller publishes them to its live map or writes them back to disk.
 */
public final class ProfileBindingCodec {
    public static final int MAX_BINDINGS = 256;
    public static final int MAX_KEY_LENGTH = 255;
    private static final String PROFILE_NAME = "[A-Za-z0-9._-]{1,32}";
    private static final Gson GSON = new Gson();

    private ProfileBindingCodec() { }

    /** Decodes and validates a legacy object-shaped binding file. */
    public static Map<String, String> decode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Profile bindings are empty");
        }
        JsonElement root = JsonParser.parseString(raw);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("Profile bindings must be a JSON object");
        }
        if (root.getAsJsonObject().size() > MAX_BINDINGS) {
            throw new IllegalArgumentException("Too many profile bindings");
        }
        Map<String, String> result = new LinkedHashMap<>();
        root.getAsJsonObject().entrySet().forEach(entry -> {
            String key = validateKey(entry.getKey());
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Profile binding values must be strings");
            }
            String profile = value.getAsString();
            if (!profile.matches(PROFILE_NAME)) {
                throw new IllegalArgumentException("Invalid profile name in binding");
            }
            result.put(key, profile);
        });
        return Collections.unmodifiableMap(result);
    }

    /** Encodes a validated map without changing its legacy on-disk shape. */
    public static String encode(Map<String, String> bindings) {
        if (bindings == null || bindings.size() > MAX_BINDINGS) {
            throw new IllegalArgumentException("Too many profile bindings");
        }
        Map<String, String> result = new LinkedHashMap<>();
        bindings.forEach((key, profile) -> {
            String validKey = validateKey(key);
            if (profile == null || !profile.matches(PROFILE_NAME)) {
                throw new IllegalArgumentException("Invalid profile name in binding");
            }
            result.put(validKey, profile);
        });
        return GSON.toJson(result);
    }

    private static String validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Invalid profile binding key");
        }
        for (int i = 0; i < key.length(); i++) {
            if (Character.isISOControl(key.charAt(i))) {
                throw new IllegalArgumentException("Invalid profile binding key");
            }
        }
        return key;
    }
}
