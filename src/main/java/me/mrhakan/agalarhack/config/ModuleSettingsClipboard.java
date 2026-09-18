package me.mrhakan.agalarhack.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import me.mrhakan.agalarhack.managers.Settings;

/** Strict clipboard boundary. Parsing has no side effects; returned keys are canonical metadata names. */
public final class ModuleSettingsClipboard {
    private static final Gson GSON = new Gson();
    private static final Set<String> PROTECTED = Set.of("enabled", "keybind", "keymodifiers", "favorite", "lastused");
    private static final int MAX_LENGTH = 65536;
    private ModuleSettingsClipboard() { }

    public static boolean editable(String key) {
        return key != null && !PROTECTED.contains(key.toLowerCase(Locale.ROOT));
    }

    public static String encode(String module, Settings settings) {
        JsonObject root = new JsonObject();
        root.addProperty("module", module);
        JsonObject values = new JsonObject();
        for (var spec : settings.getSpecs()) {
            if (editable(spec.getName())) values.add(spec.getName(), GSON.toJsonTree(settings.getSetting(spec.getName())));
        }
        root.add("settings", values);
        String result = root.toString();
        if (result.length() > MAX_LENGTH) throw new IllegalArgumentException("Settings are too large to copy");
        return result;
    }

    public static Map<String, Object> parse(String raw, String module, Settings settings) {
        if (raw == null || raw.length() > MAX_LENGTH) throw new IllegalArgumentException("Clipboard is too large");
        var document = JsonParser.parseString(raw);
        if (!document.isJsonObject()) throw new IllegalArgumentException("Expected a settings object");
        var root = document.getAsJsonObject();
        var name = root.get("module");
        if (name == null || !name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()
                || !module.equals(name.getAsString())) throw new IllegalArgumentException("Settings belong to a different module");
        var values = root.get("settings");
        if (values == null || !values.isJsonObject()) throw new IllegalArgumentException("Missing settings object");
        Map<String, Object> parsed = new LinkedHashMap<>();
        for (var entry : values.getAsJsonObject().entrySet()) {
            var spec = settings.getSpecIgnoreCase(entry.getKey());
            if (spec == null || !editable(spec.getName())) throw new IllegalArgumentException("Unknown or protected setting");
            String canonical = spec.getName();
            if (parsed.containsKey(canonical)) throw new IllegalArgumentException("Duplicate setting: " + canonical);
            if (!entry.getValue().isJsonPrimitive()) throw new IllegalArgumentException("Expected a scalar value for " + canonical);
            parsed.put(canonical, settings.parseSettingValue(canonical, entry.getValue().getAsString()));
        }
        return parsed;
    }
}
