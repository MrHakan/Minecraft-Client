package me.mrhakan.agalarhack.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import me.mrhakan.agalarhack.services.MacroDefinitions;

/**
 * Reads and writes the macro file, with the same rules as the other stores: a damaged envelope is
 * rejected so the file is preserved, while individual bad entries are skipped so the rest still load.
 */
public final class MacroCodec {
    public static final int MAX_BYTES = 32768;
    public static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private MacroCodec() { }

    public static List<MacroDefinitions.Macro> decode(String raw) {
        if (raw == null) throw new IllegalArgumentException("Missing macro data");
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Macros exceed " + MAX_BYTES + " bytes");
        }
        var root = JsonParser.parseString(raw);
        if (!root.isJsonObject()) throw new IllegalArgumentException("Macro file must be an object");
        JsonObject object = root.getAsJsonObject();
        if (object.has("schemaVersion")) {
            var version = object.get("schemaVersion");
            if (!version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Invalid macro schema version");
            }
            if (version.getAsInt() > SCHEMA_VERSION) {
                throw new IllegalArgumentException("Macro file was written by a newer version");
            }
        }
        if (!object.has("macros") || !object.get("macros").isJsonArray()) {
            throw new IllegalArgumentException("Macro file has no macro list");
        }
        List<MacroDefinitions.Macro> result = new ArrayList<>();
        for (var element : object.getAsJsonArray("macros")) {
            if (result.size() >= MacroDefinitions.MAX_MACROS) break;
            var macro = read(element);
            if (macro != null) result.add(macro);
        }
        return result;
    }

    private static MacroDefinitions.Macro read(com.google.gson.JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        try {
            var kind = MacroDefinitions.parseKind(string(object, "kind"));
            String action = string(object, "action");
            if (kind == null || action == null) return null;
            var keyElement = object.get("key");
            if (keyElement == null || !keyElement.isJsonPrimitive() || !keyElement.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            boolean mouse = object.has("mouse") && object.get("mouse").isJsonPrimitive()
                    && object.get("mouse").getAsJsonPrimitive().isBoolean() && object.get("mouse").getAsBoolean();
            return new MacroDefinitions.Macro(keyElement.getAsJsonPrimitive().getAsBigDecimal().intValueExact(),
                    mouse, kind, action);
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static String string(JsonObject object, String key) {
        var value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }

    public static String encode(List<MacroDefinitions.Macro> macros) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonArray array = new JsonArray();
        int written = 0;
        for (var macro : macros) {
            if (written++ >= MacroDefinitions.MAX_MACROS) break;
            JsonObject entry = new JsonObject();
            entry.addProperty("key", macro.key());
            entry.addProperty("mouse", macro.mouse());
            entry.addProperty("kind", macro.kind().name().toLowerCase(java.util.Locale.ROOT));
            entry.addProperty("action", macro.action());
            array.add(entry);
        }
        root.add("macros", array);
        return GSON.toJson(root);
    }
}
