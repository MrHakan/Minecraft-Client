package me.mrhakan.agalarhack.managers;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ConfigCodecTest {
    private final Gson gson = new Gson();
    @Test void legacyValuesSurviveMigrationAndRoundTrip() {
        var data = ConfigCodec.decode(JsonParser.parseString("{\"Aura\":{\"settings\":{\"range\":4.2,\"enabled\":true}}}"), gson);
        var encoded = ConfigCodec.encode(data,gson);
        assertEquals(1,encoded.get("schemaVersion").getAsInt());
        var decoded = ConfigCodec.decode(encoded,gson);
        assertEquals(4.2,((Number)decoded.get("Aura").getSetting("range")).doubleValue());
        assertEquals(true,decoded.get("Aura").getSetting("enabled"));
    }
    @Test void futureSchemaAndMalformedEnvelopeAreRejected() {
        assertThrows(ConfigCodec.UnsupportedVersionException.class, () -> ConfigCodec.decode(JsonParser.parseString("{\"schemaVersion\":99,\"modules\":{}}"),gson));
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(JsonParser.parseString("{\"schemaVersion\":1}"),gson));
        assertThrows(IllegalArgumentException.class, () -> ConfigCodec.decode(JsonParser.parseString("[]"),gson));
    }
}
