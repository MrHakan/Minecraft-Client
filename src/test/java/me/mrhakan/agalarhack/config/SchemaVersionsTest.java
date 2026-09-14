package me.mrhakan.agalarhack.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class SchemaVersionsTest {

    private static JsonObject json(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    @Test
    void aMissingVersionMeansTheFilePredatesVersioning() {
        // Every file written before the field existed had today's shape, so it is today's version.
        assertEquals(2, SchemaVersions.read(json("{}"), 2, "waypoint"));
        assertEquals(2, SchemaVersions.read(null, 2, "waypoint"));
        assertEquals(2, SchemaVersions.require(json("{}"), 2, "waypoint"));
    }

    @Test
    void aMatchingVersionIsAccepted() {
        assertEquals(1, SchemaVersions.require(json("{\"schemaVersion\":1}"), 1, "alias"));
    }

    @Test
    void anOlderVersionIsReturnedRatherThanRefused() {
        // Nothing migrates yet; the point is that the caller is told, not that it is rejected.
        assertEquals(1, SchemaVersions.require(json("{\"schemaVersion\":1}"), 3, "macro"));
    }

    @Test
    void aFutureVersionIsRefusedSoADowngradeCannotOverwriteIt() {
        var failure = assertThrows(IllegalArgumentException.class,
                () -> SchemaVersions.require(json("{\"schemaVersion\":99}"), 1, "macro"));
        assertTrue(failure.getMessage().contains("newer version"), failure.getMessage());
        assertTrue(failure.getMessage().startsWith("Macro"), "the message should name the store: " + failure.getMessage());
    }

    @Test
    void aFractionalVersionIsNotVersionOne() {
        // getAsInt would truncate this to 1 and load the file; that is the drift this class removes.
        assertThrows(IllegalArgumentException.class,
                () -> SchemaVersions.read(json("{\"schemaVersion\":1.9}"), 1, "alias"));
    }

    @Test
    void aVersionOutsideTheIntRangeIsNotAVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> SchemaVersions.read(json("{\"schemaVersion\":99999999999999999999}"), 1, "alias"));
    }

    @Test
    void aNonNumberVersionIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> SchemaVersions.read(json("{\"schemaVersion\":\"1\"}"), 1, "alias"));
        assertThrows(IllegalArgumentException.class,
                () -> SchemaVersions.read(json("{\"schemaVersion\":null}"), 1, "alias"));
        assertThrows(IllegalArgumentException.class,
                () -> SchemaVersions.read(json("{\"schemaVersion\":{}}"), 1, "alias"));
    }

    @Test
    void zeroAndNegativeVersionsAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> SchemaVersions.require(json("{\"schemaVersion\":0}"), 1, "alias"));
        assertThrows(IllegalArgumentException.class,
                () -> SchemaVersions.require(json("{\"schemaVersion\":-3}"), 1, "alias"));
    }

    @Test
    void readDoesNotRefuseAFutureVersionOnItsOwn() {
        // read reports; require decides. Keeping them apart lets a migration read first.
        assertEquals(99, SchemaVersions.read(json("{\"schemaVersion\":99}"), 1, "alias"));
    }
}
