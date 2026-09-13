package me.mrhakan.agalarhack.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import org.junit.jupiter.api.Test;

/**
 * Keeps the generated module reference honest.
 *
 * <p>Writing the page out when it differs rather than only failing is deliberate: the alternative is
 * a failure message containing a thousand lines of markdown that nobody can act on. The test still
 * fails, so it cannot pass silently in CI, but the fix is {@code git add docs/MODULES.md}.
 *
 * <p>Constructing a module turns out to work without a running client — {@code Minecraft.getInstance()}
 * simply returns null and no module touches it during construction — so this reads the real settings
 * registry rather than guessing at the source text.
 */
class ModuleDocsTest {
    private static final Path SOURCES = Path.of("src/main/java/me/mrhakan/agalarhack/module");
    private static final Path PAGE = Path.of("docs/MODULES.md");
    private static final String PACKAGE = "me.mrhakan.agalarhack.module.";

    private static List<Module> allModules() throws IOException {
        List<Module> modules = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                String simple = file.getFileName().toString().replace(".java", "");
                if (simple.equals("Module") || simple.equals("Category")) continue;
                String pkg = SOURCES.relativize(file.getParent()).toString().replace('/', '.');
                String className = PACKAGE + (pkg.isEmpty() ? "" : pkg + ".") + simple;
                Module module = (Module) Class.forName(className).getDeclaredConstructor().newInstance();
                module.registerSettings();
                modules.add(module);
            }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Every module needs a public no-argument constructor", failure);
        }
        return modules;
    }

    @Test
    void generatedPageMatchesTheModulesOnDisk() throws IOException {
        String expected = ModuleDocs.render(allModules());
        String actual = Files.exists(PAGE) ? Files.readString(PAGE) : "";
        if (!expected.equals(actual)) {
            Files.createDirectories(PAGE.getParent());
            Files.writeString(PAGE, expected);
        }
        assertEquals(expected, actual,
                "docs/MODULES.md was out of date and has been rewritten; commit it (git add " + PAGE + ")");
    }

    @Test
    void everyModuleIsDocumentedUnderItsOwnCategory() throws IOException {
        List<Module> modules = allModules();
        String page = ModuleDocs.render(modules);
        for (Module module : modules) {
            assertTrue(page.contains("### " + module.getName()), "missing from the page: " + module.getName());
        }
        for (Category category : Category.values()) {
            boolean used = modules.stream().anyMatch(module -> module.getCategory() == category);
            assertEquals(used, page.contains("## " + category.name),
                    "category heading and membership disagree for " + category.name);
        }
    }

    @Test
    void sharedSettingsAreNotRepeatedOnEveryModule() throws IOException {
        String page = ModuleDocs.render(allModules());
        for (String shared : ModuleDocs.SHARED_SETTINGS) {
            assertFalse(page.contains("| `" + shared + "` |"),
                    shared + " is on every module; repeating it would bury the settings that matter");
        }
    }

    @Test
    void pipesInDescriptionsCannotBreakATableRow() {
        Module module = new Module("Probe", Category.MISC, "probe") {
            @Override
            public void selfSettings() {
                addChoiceSetting("mode", "a", "Either a | b", "a", "b");
            }
        };
        module.registerSettings();
        String page = ModuleDocs.render(List.of(module));
        for (String line : page.lines().filter(row -> row.startsWith("| `mode`")).toList() ) {
            // Four columns means five pipes; an unescaped one in the text would make six.
            assertEquals(5, line.chars().filter(character -> character == '|').count()
                    - line.split("\\\\\\|", -1).length + 1,
                    "unescaped pipe in: " + line);
        }
    }

    @Test
    void aModuleWithNoSettingsOfItsOwnStillGetsAnEntry() {
        Module module = new Module("Bare", Category.MISC, "nothing to configure") { };
        module.registerSettings();
        String page = ModuleDocs.render(List.of(module));
        assertTrue(page.contains("### Bare"));
        assertTrue(page.contains("No settings of its own."));
    }
}
