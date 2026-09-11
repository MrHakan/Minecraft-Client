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

    /**
     * Modules whose flag has been cleared, each mapped to the scenario in
     * {@code ModuleBehaviourGameTest} that earned it.
     *
     * <p>A name can only be added here alongside a scenario that actually exists - the test below
     * checks that - so clearing a flag cannot be done by editing a list. That is the whole point of
     * the flag: it says a human or a running game confirmed the module works, and a list anyone can
     * append to says nothing.
     */
    private static final java.util.Map<String, String> VERIFIED_IN_GAME = java.util.Map.ofEntries(
            java.util.Map.entry("AutoTotem.java", "autoTotem"),
            java.util.Map.entry("AutoArmor.java", "autoArmor"),
            java.util.Map.entry("CameraTweaks.java", "cameraTweaks"),
            java.util.Map.entry("AutoWalk.java", "autoWalk"),
            java.util.Map.entry("SafeWalk.java", "safeWalk"),
            java.util.Map.entry("Parkour.java", "parkour"),
            java.util.Map.entry("AutoRefill.java", "autoRefill"),
            java.util.Map.entry("InventoryCleaner.java", "inventoryCleaner"),
            java.util.Map.entry("AutoWeapon.java", "autoWeapon"),
            java.util.Map.entry("BetterChat.java", "betterChat"),
            java.util.Map.entry("ChatFilter.java", "chatFilter"),
            java.util.Map.entry("ChatMentions.java", "chatMentions"),
            java.util.Map.entry("AutoAccept.java", "autoAccept"),
            java.util.Map.entry("HoleESP.java", "holeEsp"));

    private static final Path BEHAVIOUR_TEST =
            Path.of("src/gametest/java/me/mrhakan/agalarhack/gametest/ModuleBehaviourGameTest.java");

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
            if (PRE_EXISTING.contains(name) || VERIFIED_IN_GAME.containsKey(name)) continue;
            if (!Files.readString(file).contains("markExperimental()")) unmarked.add(name);
        }
        assertTrue(unmarked.isEmpty(),
                "New modules must be marked experimental until verified in game: " + unmarked);
    }

    @Test void clearedFlagsNameAScenarioThatExists() throws IOException {
        String behaviour = Files.readString(BEHAVIOUR_TEST);
        List<String> missing = new ArrayList<>();
        for (var entry : VERIFIED_IN_GAME.entrySet()) {
            if (!behaviour.contains("void " + entry.getValue() + "(")) {
                missing.add(entry.getKey() + " -> " + entry.getValue());
            }
        }
        assertTrue(missing.isEmpty(),
                "A cleared flag must point at a game test scenario that still exists: " + missing);
    }

    @Test void modulesVerifiedInGameNoLongerCarryTheFlag() throws IOException {
        List<String> stale = new ArrayList<>();
        for (Path file : moduleFiles()) {
            String name = file.getFileName().toString();
            if (!VERIFIED_IN_GAME.containsKey(name)) continue;
            if (Files.readString(file).contains("markExperimental()")) stale.add(name);
        }
        assertTrue(stale.isEmpty(), "Verified modules should have had the flag removed: " + stale);
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
