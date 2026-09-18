package me.mrhakan.agalarhack.config;

import me.mrhakan.agalarhack.services.Waypoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaypointCodecTest {
    @Test void roundTripPreservesEveryField() {
        var original = new java.util.LinkedHashMap<String, Waypoint>();
        var point = new Waypoint("Base", 100, 64, -200, "minecraft:the_nether", 0xFFAA3311, false, true);
        original.put(point.key(), point);
        var decoded = WaypointCodec.decode(WaypointCodec.encode(original));
        assertEquals(1, decoded.size());
        assertEquals(point, decoded.get(point.key()));
    }

    @Test void oneMalformedEntryDoesNotCostTheOthers() {
        String raw = """
            {"schemaVersion":1,"waypoints":[
              {"name":"good","x":1,"y":2,"z":3},
              {"x":9,"y":9,"z":9},
              "not an object",
              {"name":"   ","x":0,"y":0,"z":0},
              {"name":"alsogood","x":4,"y":5,"z":6}
            ]}""";
        var decoded = WaypointCodec.decode(raw);
        assertEquals(2, decoded.size());
        assertTrue(decoded.containsKey("minecraft:overworld/good"));
        assertTrue(decoded.containsKey("minecraft:overworld/alsogood"));
    }

    @Test void missingOptionalFieldsFallBackToDefaults() {
        var decoded = WaypointCodec.decode("{\"waypoints\":[{\"name\":\"a\",\"x\":1,\"y\":2,\"z\":3}]}");
        var point = decoded.values().iterator().next();
        assertEquals(Waypoint.DEFAULT_DIMENSION, point.dimension());
        assertEquals(Waypoint.DEFAULT_COLOR, point.color());
        assertTrue(point.visible());
        assertFalse(point.beam());
    }

    @Test void aFutureVersionIsRejectedSoTheFileIsPreserved() {
        assertThrows(IllegalArgumentException.class,
                () -> WaypointCodec.decode("{\"schemaVersion\":99,\"waypoints\":[]}"));
    }

    @Test void structuralDamageIsRejectedRatherThanSilentlyEmptied() {
        assertThrows(RuntimeException.class, () -> WaypointCodec.decode("[]"));
        assertThrows(RuntimeException.class, () -> WaypointCodec.decode("{\"schemaVersion\":1}"));
        assertThrows(RuntimeException.class, () -> WaypointCodec.decode("{\"waypoints\":{}}"));
        assertThrows(RuntimeException.class, () -> WaypointCodec.decode("not json"));
        assertThrows(IllegalArgumentException.class, () -> WaypointCodec.decode(null));
        assertThrows(IllegalArgumentException.class,
                () -> WaypointCodec.decode("{\"schemaVersion\":\"one\",\"waypoints\":[]}"));
    }

    @Test void oversizedFilesAreRejectedBeforeParsing() {
        String padded = "{\"waypoints\":[]," + "\"pad\":\"" + "x".repeat(WaypointCodec.MAX_BYTES) + "\"}";
        assertThrows(IllegalArgumentException.class, () -> WaypointCodec.decode(padded));
    }

    @Test void multibyteNamesCountAsBytesNotCharacters() {
        String name = "ü".repeat(8);
        var map = new java.util.LinkedHashMap<String, Waypoint>();
        var point = Waypoint.of(name, 0, 0, 0, null);
        map.put(point.key(), point);
        assertEquals(point.name(), WaypointCodec.decode(WaypointCodec.encode(map)).values().iterator().next().name());
    }

    @Test void decodingIsBoundedByTheWaypointLimit() {
        StringBuilder raw = new StringBuilder("{\"schemaVersion\":1,\"waypoints\":[");
        for (int i = 0; i < WaypointCodec.MAX_WAYPOINTS + 50; i++) {
            if (i > 0) raw.append(',');
            raw.append("{\"name\":\"w").append(i).append("\",\"x\":0,\"y\":0,\"z\":0}");
        }
        raw.append("]}");
        assertEquals(WaypointCodec.MAX_WAYPOINTS, WaypointCodec.decode(raw.toString()).size());
    }

    @Test void anEmptyListIsValid() {
        assertTrue(WaypointCodec.decode("{\"schemaVersion\":1,\"waypoints\":[]}").isEmpty());
    }
}
