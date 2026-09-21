package me.mrhakan.agalarhack.config;

import me.mrhakan.agalarhack.services.MacroDefinitions;
import me.mrhakan.agalarhack.services.MacroDefinitions.Kind;
import me.mrhakan.agalarhack.services.MacroDefinitions.Macro;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MacroCodecTest {
    @Test void roundTripPreservesEveryField() {
        var original = java.util.List.of(
                new Macro(72, false, Kind.CHAT, "hello there"),
                new Macro(-101, true, Kind.TOGGLE, "Flight"));
        assertEquals(original, MacroCodec.decode(MacroCodec.encode(original)));
    }

    @Test void unusableEntriesAreSkippedSoTheRestStillLoad() {
        String raw = """
            {"schemaVersion":1,"macros":[
              {"key":72,"mouse":false,"kind":"chat","action":"good"},
              {"key":"notanumber","kind":"chat","action":"bad"},
              {"key":73,"kind":"nonsense","action":"bad"},
              {"key":74,"kind":"chat"},
              "not an object"
            ]}""";
        var decoded = MacroCodec.decode(raw);
        assertEquals(1, decoded.size());
        assertEquals("good", decoded.get(0).action());
    }

    @Test void missingMouseFlagDefaultsToKeyboard() {
        var decoded = MacroCodec.decode("{\"macros\":[{\"key\":72,\"kind\":\"cmd\",\"action\":\"toggle Flight\"}]}");
        assertFalse(decoded.get(0).mouse());
        assertEquals(Kind.COMMAND, decoded.get(0).kind());
    }

    @Test void structuralDamageIsRejectedSoTheFileIsPreserved() {
        assertThrows(RuntimeException.class, () -> MacroCodec.decode("[]"));
        assertThrows(RuntimeException.class, () -> MacroCodec.decode("{\"schemaVersion\":1}"));
        assertThrows(RuntimeException.class, () -> MacroCodec.decode("{\"macros\":{}}"));
        assertThrows(IllegalArgumentException.class, () -> MacroCodec.decode(null));
        assertThrows(IllegalArgumentException.class,
                () -> MacroCodec.decode("{\"schemaVersion\":99,\"macros\":[]}"));
    }

    @Test void oversizedFilesAreRejectedBeforeParsing() {
        String padded = "{\"macros\":[],\"pad\":\"" + "x".repeat(MacroCodec.MAX_BYTES) + "\"}";
        assertThrows(IllegalArgumentException.class, () -> MacroCodec.decode(padded));
    }

    @Test void decodingIsBoundedByTheMacroLimit() {
        var many = new java.util.ArrayList<Macro>();
        for (int i = 0; i < MacroDefinitions.MAX_MACROS + 20; i++) many.add(new Macro(i, false, Kind.CHAT, "x"));
        assertEquals(MacroDefinitions.MAX_MACROS, MacroCodec.decode(MacroCodec.encode(many)).size());
    }
}
