package me.mrhakan.agalarhack.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import me.mrhakan.agalarhack.services.CommandAliases;

/**
 * Reads and writes the alias file.
 *
 * <p>Like the waypoint file, the envelope is strict so a damaged file is preserved rather than
 * overwritten, while individual unusable entries are skipped instead of failing the whole load -
 * the aliases that are still valid keep working.
 */
public final class AliasCodec {
    public static final int MAX_BYTES = 32768;
    public static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AliasCodec() { }

    public static Map<String, String> decode(String raw) {
        if (raw == null) throw new IllegalArgumentException("Missing alias data");
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Aliases exceed " + MAX_BYTES + " bytes");
        }
        var root = JsonParser.parseString(raw);
        if (!root.isJsonObject()) throw new IllegalArgumentException("Alias file must be an object");
        JsonObject object = root.getAsJsonObject();
        SchemaVersions.require(object, SCHEMA_VERSION, "alias");
        if (!object.has("aliases") || !object.get("aliases").isJsonObject()) {
            throw new IllegalArgumentException("Alias file has no alias map");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : object.getAsJsonObject("aliases").entrySet()) {
            if (result.size() >= CommandAliases.MAX_ALIASES) break;
            var value = entry.getValue();
            // Skip anything that is not a plain string; the rest of the file still loads.
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                result.put(entry.getKey(), value.getAsString());
            }
        }
        return result;
    }

    public static String encode(Map<String, String> aliases) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonObject map = new JsonObject();
        int written = 0;
        for (var entry : aliases.entrySet()) {
            if (written++ >= CommandAliases.MAX_ALIASES) break;
            map.addProperty(entry.getKey(), entry.getValue());
        }
        root.add("aliases", map);
        return GSON.toJson(root);
    }
}
