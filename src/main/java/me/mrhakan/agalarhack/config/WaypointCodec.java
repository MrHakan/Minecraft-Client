package me.mrhakan.agalarhack.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.mrhakan.agalarhack.services.Waypoint;

/**
 * Reads and writes the waypoint file.
 *
 * <p>Waypoints are user data that a crash or a bad edit must not destroy, so decoding is strict
 * about the envelope and forgiving about individual entries: a single malformed waypoint is skipped
 * rather than failing the whole file, while an unreadable or future-versioned file is rejected
 * outright so {@link BoundedJsonFile} preserves it instead of overwriting.
 */
public final class WaypointCodec {
    public static final int MAX_BYTES = 262144;
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_WAYPOINTS = 512;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private WaypointCodec() { }

    public static Map<String, Waypoint> decode(String raw) {
        if (raw == null) throw new IllegalArgumentException("Missing waypoint data");
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Waypoints exceed " + MAX_BYTES + " bytes");
        }
        var root = JsonParser.parseString(raw);
        if (!root.isJsonObject()) throw new IllegalArgumentException("Waypoint file must be an object");
        JsonObject object = root.getAsJsonObject();
        SchemaVersions.require(object, SCHEMA_VERSION, "waypoint");
        if (!object.has("waypoints") || !object.get("waypoints").isJsonArray()) {
            throw new IllegalArgumentException("Waypoint file has no waypoint list");
        }
        Map<String, Waypoint> result = new LinkedHashMap<>();
        JsonArray array = object.getAsJsonArray("waypoints");
        for (var element : array) {
            if (result.size() >= MAX_WAYPOINTS) break;
            Waypoint waypoint = readWaypoint(element);
            // One bad entry must not cost the player every other waypoint.
            if (waypoint != null) result.put(waypoint.key(), waypoint);
        }
        return result;
    }

    private static Waypoint readWaypoint(com.google.gson.JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        try {
            String name = string(object, "name");
            if (name == null) return null;
            return new Waypoint(name,
                    integer(object, "x", 0), integer(object, "y", 0), integer(object, "z", 0),
                    string(object, "dimension"),
                    integer(object, "color", Waypoint.DEFAULT_COLOR),
                    bool(object, "visible", true), bool(object, "beam", false));
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static String string(JsonObject object, String key) {
        var value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        var value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
        try {
            return value.getAsJsonPrimitive().getAsBigDecimal().intValueExact();
        } catch (ArithmeticException notAnInteger) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        var value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                ? value.getAsBoolean() : fallback;
    }

    public static String encode(Map<String, Waypoint> waypoints) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonArray array = new JsonArray();
        List<Waypoint> ordered = new ArrayList<>(waypoints.values());
        for (int index = 0; index < ordered.size() && index < MAX_WAYPOINTS; index++) {
            Waypoint waypoint = ordered.get(index);
            JsonObject entry = new JsonObject();
            entry.addProperty("name", waypoint.name());
            entry.addProperty("x", waypoint.x());
            entry.addProperty("y", waypoint.y());
            entry.addProperty("z", waypoint.z());
            entry.addProperty("dimension", waypoint.dimension());
            entry.addProperty("color", waypoint.color());
            entry.addProperty("visible", waypoint.visible());
            entry.addProperty("beam", waypoint.beam());
            array.add(entry);
        }
        root.add("waypoints", array);
        return GSON.toJson(root);
    }
}
