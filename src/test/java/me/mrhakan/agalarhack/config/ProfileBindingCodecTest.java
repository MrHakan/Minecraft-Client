package me.mrhakan.agalarhack.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class ProfileBindingCodecTest {
    @Test
    void legacyObjectRoundTripsInInsertionOrder() {
        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put("play.example:25565", "combat");
        bindings.put("minecraft:the_nether", "nether");

        Map<String, String> decoded = ProfileBindingCodec.decode(ProfileBindingCodec.encode(bindings));

        assertEquals(bindings, decoded);
        assertEquals(bindings.keySet().toString(), decoded.keySet().toString());
    }

    @Test
    void missingAndNonObjectFilesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProfileBindingCodec.decode(""));
        assertThrows(IllegalArgumentException.class, () -> ProfileBindingCodec.decode("[]"));
        assertThrows(IllegalArgumentException.class, () -> ProfileBindingCodec.decode("null"));
    }

    @Test
    void valuesMustBeStringsWithValidProfileNames() {
        assertThrows(IllegalArgumentException.class,
                () -> ProfileBindingCodec.decode("{\"server\": 1}"));
        assertThrows(IllegalArgumentException.class,
                () -> ProfileBindingCodec.decode("{\"server\": \"bad name\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> ProfileBindingCodec.decode("{\"server\": \"\"}"));
    }

    @Test
    void keysAreBoundedAndControlCharactersAreRejected() {
        String longKey = "x".repeat(ProfileBindingCodec.MAX_KEY_LENGTH + 1);
        assertThrows(IllegalArgumentException.class,
                () -> ProfileBindingCodec.decode("{\"" + longKey + "\":\"profile\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> ProfileBindingCodec.decode("{\"line\\nfeed\":\"profile\"}"));
    }

    @Test
    void tooManyEntriesAreRejectedBeforeTheyReachTheLiveMap() {
        String json = "{" + IntStream.range(0, ProfileBindingCodec.MAX_BINDINGS + 1)
                .mapToObj(i -> "\\\"server" + i + "\\\":\\\"profile\\\"")
                .collect(Collectors.joining(",")) + "}";
        assertThrows(IllegalArgumentException.class, () -> ProfileBindingCodec.decode(json));

        Map<String, String> tooMany = new LinkedHashMap<>();
        for (int i = 0; i <= ProfileBindingCodec.MAX_BINDINGS; i++) {
            tooMany.put("server" + i, "profile");
        }
        assertThrows(IllegalArgumentException.class, () -> ProfileBindingCodec.encode(tooMany));
    }

    @Test
    void encodeRejectsInvalidKeysAndProfiles() {
        assertThrows(IllegalArgumentException.class,
                () -> ProfileBindingCodec.encode(Map.of("", "profile")));
        assertThrows(IllegalArgumentException.class,
                () -> ProfileBindingCodec.encode(Map.of("server", "profile/name")));
    }
}
