package me.mrhakan.agalarhack.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ThemeCodecTest {
    @TempDir Path directory;

    @Test void legacyDefaultsRoundTripAndBoundsRemainCompatible() {
        var theme = ThemeCodec.decode("{\"name\":\"Legacy\",\"panelOpacity\":4,\"cornerRadius\":-2}");
        assertEquals(1, theme.schemaVersion);
        assertTrue(theme.uiAnimations);
        assertEquals(1, theme.panelOpacity);
        assertEquals(0, theme.cornerRadius);
        var copy = ThemeCodec.copy(theme);
        copy.name = "Edited";
        assertEquals("Legacy", theme.name);
        assertEquals(theme.accent, copy.accent);
    }

    @Test void rejectsCoercionsFutureSchemasAndNonObjects() {
        for (String raw : new String[]{"null", "[]", "{\"schemaVersion\":2}", "{\"schemaVersion\":1.2}",
                "{\"accent\":4294967295}", "{\"uiAnimations\":\"false\"}", "{\"panelOpacity\":\"NaN\"}",
                "{\"name\":null}", "{\"unknown\":true}"}) {
            assertThrows(RuntimeException.class, () -> ThemeCodec.decode(raw), raw);
        }
    }

    @Test void multibyteImportsRespectByteLimit() {
        assertThrows(IllegalArgumentException.class, () -> ThemeCodec.decode("{\"name\":\"" + "界".repeat(6000) + "\"}"));
    }

    @Test void reducedMotionOverridesAnimationsAndSurvivesExport() {
        var theme = ThemeCodec.decode("{\"reducedMotion\":true,\"highContrast\":true,\"uiAnimations\":true}");
        assertFalse(theme.motionEnabled());
        var restored = ThemeCodec.decode(ThemeCodec.encode(theme));
        assertTrue(restored.highContrast);
        assertFalse(restored.motionEnabled());
        restored.reducedMotion = false;
        assertTrue(restored.motionEnabled());
        restored.uiAnimations = false;
        assertFalse(restored.motionEnabled());
        assertFalse(ThemeCodec.decode("{}").highContrast);
    }

    @Test void invalidThemeIsPreservedAndSuccessfulReloadUnlocksSaving() throws Exception {
        Path path = directory.resolve("theme.json");
        Files.writeString(path, "{\"schemaVersion\":2}");
        var file = new BoundedJsonFile<>(path, ThemeCodec.MAX_BYTES, ThemeCodec::decode, ThemeCodec::encode);
        assertThrows(IOException.class, file::load);
        assertThrows(IOException.class, () -> file.save(ThemeCodec.decode("{}")));
        assertEquals("{\"schemaVersion\":2}", Files.readString(path));
        Files.writeString(path, "{\"name\":\"Repaired\"}");
        var theme = file.load().orElseThrow();
        theme.name = "Saved";
        file.save(theme);
        assertEquals("Saved", file.load().orElseThrow().name);
    }
}
