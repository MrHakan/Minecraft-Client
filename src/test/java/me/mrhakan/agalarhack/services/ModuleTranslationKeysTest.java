package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A translated module description is keyed by the module's name, so renaming a module silently
 * orphans its translation — the client falls back to English and nothing says why. These check the
 * two directions that can drift.
 */
class ModuleTranslationKeysTest {
    private static final Path LANG = Path.of("src/main/resources/assets/agalarhack/lang");
    private static final Path MODULES = Path.of("src/main/java/me/mrhakan/agalarhack/module");
    private static final String PREFIX = "agalarhack.module.";
    private static final String SUFFIX = ".description";

    /** Module names taken from their own constructor call, which is where the name really lives. */
    private static Set<String> moduleNames() throws IOException {
        Set<String> names = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(MODULES)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java")).toList()) {
                String simple = file.getFileName().toString().replace(".java", "");
                if (simple.equals("Module") || simple.equals("Category")) continue;
                var matcher = java.util.regex.Pattern
                        .compile("super\\(\\s*\"([^\"]+)\"\\s*,\\s*Category\\.")
                        .matcher(Files.readString(file));
                assertTrue(matcher.find(), simple + " does not name itself in a super() call");
                names.add(matcher.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    private static JsonObject language(String file) throws IOException {
        return JsonParser.parseString(Files.readString(LANG.resolve(file), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void everyTranslatedDescriptionNamesARealModule() throws IOException {
        Set<String> names = moduleNames();
        List<String> orphans = new ArrayList<>();
        for (String key : language("tr_tr.json").keySet()) {
            if (!key.startsWith(PREFIX) || !key.endsWith(SUFFIX)) continue;
            String name = key.substring(PREFIX.length(), key.length() - SUFFIX.length());
            if (!names.contains(name)) orphans.add(key);
        }
        assertTrue(orphans.isEmpty(), "these translations name no module, probably after a rename: " + orphans);
    }

    @Test
    void everyModuleHasATurkishDescription() throws IOException {
        JsonObject turkish = language("tr_tr.json");
        List<String> missing = new ArrayList<>();
        for (String name : moduleNames()) {
            if (!turkish.has(PREFIX + name + SUFFIX)) missing.add(name);
        }
        assertTrue(missing.isEmpty(), "untranslated modules: " + missing);
    }

    @Test
    void noTranslationIsBlankOrLeftAsTheKey() throws IOException {
        JsonObject turkish = language("tr_tr.json");
        List<String> bad = new ArrayList<>();
        for (String key : turkish.keySet()) {
            String value = turkish.get(key).getAsString();
            if (value.isBlank() || value.equals(key)) bad.add(key);
        }
        assertTrue(bad.isEmpty(), "blank or placeholder translations: " + bad);
    }

    @Test
    void theEnglishFileStillCoversTheGuiKeys() throws IOException {
        // Module descriptions fall back to the constructor text, so they are deliberately absent
        // from en_us; the GUI keys are not, and losing one there would show a raw key.
        JsonObject english = language("en_us.json");
        List<String> missing = new ArrayList<>();
        for (String key : language("tr_tr.json").keySet()) {
            if (key.startsWith(PREFIX)) continue;
            if (!english.has(key)) missing.add(key);
        }
        assertEquals(List.of(), missing);
    }
}
