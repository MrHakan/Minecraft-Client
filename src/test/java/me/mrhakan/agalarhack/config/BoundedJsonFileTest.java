package me.mrhakan.agalarhack.config;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BoundedJsonFileTest {
    @TempDir Path directory;
    private BoundedJsonFile<String> file(Path path) {
        return new BoundedJsonFile<>(path, 128, raw -> {
            var object = JsonParser.parseString(raw).getAsJsonObject();
            if (object.get("version").getAsInt() != 1) throw new IllegalArgumentException("Future schema");
            return raw;
        }, value -> value);
    }
    @Test void invalidFutureAndOversizedFilesCannotBeOverwritten() throws Exception {
        Path path = directory.resolve("config.json");
        for (String content : new String[]{"{broken", "{\"version\":2}", "x".repeat(129), "null"}) {
            Files.writeString(path, content);
            var file = file(path);
            assertThrows(IOException.class, file::load);
            assertThrows(IOException.class, () -> file.save("{\"version\":1}"));
            assertEquals(content, Files.readString(path));
        }
    }
    @Test void freshFileRoundTripsAndFailedWriteRetainsPreviousBytes() throws Exception {
        Path path = directory.resolve("nested/config.json");
        var file = file(path);
        assertTrue(file.load().isEmpty());
        String saved = "{\"version\":1}";
        file.save(saved);
        assertEquals(saved, file.load().orElseThrow());
        assertThrows(IOException.class, () -> file.save("x".repeat(129)));
        assertEquals(saved, Files.readString(path));
        try (var entries = Files.list(path.getParent())) { assertEquals(1, entries.count()); }
    }
}
