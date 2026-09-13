package me.mrhakan.agalarhack.managers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.util.Map;

/** Explicit migration from legacy bare module maps (v0) to a versioned envelope (v1). */
public final class ConfigCodec {
    public static final int CURRENT_VERSION = 1;
    private ConfigCodec() { }
    public static final class UnsupportedVersionException extends IllegalArgumentException {
        public UnsupportedVersionException(String message) { super(message); }
    }
    public static Map<String, Settings> decode(JsonElement document, Gson gson) {
        if (document == null || !document.isJsonObject()) throw new IllegalArgumentException("Config must be an object");
        JsonObject root = document.getAsJsonObject();
        JsonObject modules = root;
        if (root.has("schemaVersion")) {
            JsonElement version = root.get("schemaVersion");
            if (!version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()
                    || version.getAsDouble() != CURRENT_VERSION) {
                throw new UnsupportedVersionException("Unsupported config schema version; original file retained");
            }
            if (!root.has("modules") || !root.get("modules").isJsonObject()) throw new IllegalArgumentException("Missing modules object");
            modules = root.getAsJsonObject("modules");
        }
        return gson.fromJson(modules, new TypeToken<Map<String, Settings>>() {}.getType());
    }
    public static JsonObject encode(Map<String, Settings> modules, Gson gson) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", CURRENT_VERSION);
        root.add("modules", gson.toJsonTree(modules));
        return root;
    }
}
