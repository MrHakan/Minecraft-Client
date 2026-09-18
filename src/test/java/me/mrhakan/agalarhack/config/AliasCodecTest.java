package me.mrhakan.agalarhack.config;

import me.mrhakan.agalarhack.services.CommandAliases;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AliasCodecTest {
    @Test void roundTripPreservesAliases() {
        var original = new java.util.LinkedHashMap<String, String>();
        original.put("home", "waypoint goto home");
        original.put("wp", "waypoint");
        assertEquals(original, AliasCodec.decode(AliasCodec.encode(original)));
    }

    @Test void unusableEntriesAreSkippedSoTheRestStillLoads() {
        String raw = "{\"schemaVersion\":1,\"aliases\":{\"home\":\"waypoint goto home\",\"bad\":123,\"other\":{}}}";
        var decoded = AliasCodec.decode(raw);
        assertEquals(1, decoded.size());
        assertEquals("waypoint goto home", decoded.get("home"));
    }

    @Test void structuralDamageIsRejectedSoTheFileIsPreserved() {
        assertThrows(RuntimeException.class, () -> AliasCodec.decode("[]"));
        assertThrows(RuntimeException.class, () -> AliasCodec.decode("{\"schemaVersion\":1}"));
        assertThrows(RuntimeException.class, () -> AliasCodec.decode("{\"aliases\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> AliasCodec.decode(null));
        assertThrows(IllegalArgumentException.class,
                () -> AliasCodec.decode("{\"schemaVersion\":99,\"aliases\":{}}"));
    }

    @Test void oversizedFilesAreRejectedBeforeParsing() {
        String padded = "{\"aliases\":{},\"pad\":\"" + "x".repeat(AliasCodec.MAX_BYTES) + "\"}";
        assertThrows(IllegalArgumentException.class, () -> AliasCodec.decode(padded));
    }

    @Test void decodingIsBoundedByTheAliasLimit() {
        var many = new java.util.LinkedHashMap<String, String>();
        for (int i = 0; i < CommandAliases.MAX_ALIASES + 20; i++) many.put("a" + i, "toggle x");
        assertEquals(CommandAliases.MAX_ALIASES, AliasCodec.decode(AliasCodec.encode(many)).size());
    }

    @Test void loadingRevalidatesHandEditedEntries() {
        var aliases = new CommandAliases();
        var stored = new java.util.LinkedHashMap<String, String>();
        stored.put("good", "waypoint goto home");
        stored.put("two words", "toggle flight");
        stored.put("self", "self loop");
        aliases.replaceAll(stored);
        assertEquals(1, aliases.size(), "hand-edited nonsense must not survive loading");
        assertTrue(aliases.expand("good").isPresent());
    }
}
