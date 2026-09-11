package me.mrhakan.agalarhack.module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the experimental marking by reading the sources directly.
 *
 * <p>Constructing a Module needs a Minecraft client, so this checks the source text instead. It is
 * coarse on purpose: the point is that a module added later cannot quietly ship unmarked, not that
 * every existing module is classified perfectly.
 */
class ExperimentalFlagTest {
    private static final Path MODULES = Path.of("src/main/java/me/mrhakan/agalarhack/module");

    /** Modules that predate this work and were shipped before; they are not marked. */
    private static final List<String> PRE_EXISTING = List.of(
            "Aura.java", "TriggerBot.java", "AutoEat.java", "AutoReconnect.java", "AutoTool.java",
            "Flight.java", "Jesus.java", "NoFall.java", "Speed.java", "Sprint.java", "Step.java",
            "BlockESP.java", "Coordinates.java", "Durability.java", "EntityESP.java", "Freecam.java",
            "Fullbright.java", "ModuleList.java", "Notifications.java", "StorageESP.java",
            "TargetHUD.java", "Trajectories.java");

    private static List<Path> moduleFiles() throws IOException {
        try (Stream<Path> files = Files.walk(MODULES)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("Module.java"))
                    .filter(path -> !path.getFileName().toString().equals("Category.java"))
                    .toList();
        }
    }

    @Test void everyModuleAddedInThisWorkIsMarkedUntested() throws IOException {
        List<String> unmarked = new ArrayList<>();
        for (Path file : moduleFiles()) {
            String name = file.getFileName().toString();
            if (PRE_EXISTING.contains(name)) continue;
            if (!Files.readString(file).contains("markExperimental()")) unmarked.add(name);
        }
        assertTrue(unmarked.isEmpty(),
                "New modules must be marked experimental until verified in game: " + unmarked);
    }

    @Test void preExistingModulesAreNotMarked() throws IOException {
        List<String> wronglyMarked = new ArrayList<>();
        for (Path file : moduleFiles()) {
            String name = file.getFileName().toString();
            if (!PRE_EXISTING.contains(name)) continue;
            if (Files.readString(file).contains("markExperimental()")) wronglyMarked.add(name);
        }
        assertTrue(wronglyMarked.isEmpty(), "Shipped modules should not be marked: " + wronglyMarked);
    }

    @Test void theModuleDirectoryIsWhereThisTestThinksItIs() throws IOException {
        assertTrue(Files.isDirectory(MODULES), "module sources moved; update this test");
        assertFalse(moduleFiles().isEmpty());
    }
}
