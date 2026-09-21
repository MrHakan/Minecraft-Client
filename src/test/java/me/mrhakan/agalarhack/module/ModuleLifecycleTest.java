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
 * Standing guard for the lifecycle rules the roadmap asks for.
 *
 * <p>A module that takes something - an event subscription, ownership of the hotbar or the container
 * channel - has to give it back. That was audited by hand once; this keeps it true as modules are
 * added, since a leaked subscription or a never-released lease is invisible until it strands a
 * player's inventory or fires callbacks for a world that is gone.
 *
 * <p>Source-level and therefore coarse: it cannot prove the release happens on every path, only that
 * the module does not obviously forget. It is a floor, not a proof.
 */
class ModuleLifecycleTest {
    private static final Path MODULES = Path.of("src/main/java/me/mrhakan/agalarhack/module");

    private static List<Path> moduleFiles() throws IOException {
        try (Stream<Path> files = Files.walk(MODULES)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("Module.java"))
                    .filter(path -> !path.getFileName().toString().equals("Category.java"))
                    .toList();
        }
    }

    @Test void everySubscriberClosesItsSubscriptionsAndHasSomewhereToDoIt() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : moduleFiles()) {
            String source = Files.readString(file);
            if (!source.contains("EventBus.Subscription")) continue;
            boolean closes = source.contains(".close()");
            boolean disables = source.contains("public void onDisable");
            if (!closes || !disables) offenders.add(file.getFileName() + " (closes=" + closes + ", onDisable=" + disables + ")");
        }
        assertTrue(offenders.isEmpty(),
                "Modules holding event subscriptions must close them from onDisable: " + offenders);
    }

    @Test void everyInventoryOwnerReleasesItsClaim() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : moduleFiles()) {
            String source = Files.readString(file);
            if (!source.contains("String OWNER")) continue;
            boolean releases = source.contains("release(OWNER)");
            boolean disables = source.contains("public void onDisable");
            if (!releases || !disables) offenders.add(file.getFileName() + " (releases=" + releases + ", onDisable=" + disables + ")");
        }
        assertTrue(offenders.isEmpty(),
                "Modules claiming hotbar or container ownership must release it: " + offenders);
    }

    @Test void everySchedulerConsumerCancelsItsScannerWork() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : moduleFiles()) {
            String source = Files.readString(file);
            if (!source.contains("ScannerService.class).offer(")) continue;
            if (!source.contains("cancel(this)")) offenders.add(file.getFileName().toString());
        }
        assertTrue(offenders.isEmpty(),
                "Modules offering scanner work must cancel it on disable: " + offenders);
    }

    @Test void theAuditIsActuallyLookingAtModules() throws IOException {
        var files = moduleFiles();
        assertFalse(files.isEmpty(), "module sources moved; update this test");
        // Sanity: the patterns above must match something, or the guards are silently vacuous.
        long subscribers = 0;
        long owners = 0;
        long scanners = 0;
        for (Path file : files) {
            String source = Files.readString(file);
            if (source.contains("EventBus.Subscription")) subscribers++;
            if (source.contains("String OWNER")) owners++;
            if (source.contains("ScannerService.class).offer(")) scanners++;
        }
        assertTrue(subscribers > 0, "no subscriber modules found; the guard would pass vacuously");
        assertTrue(owners > 0, "no inventory-owning modules found; the guard would pass vacuously");
        assertTrue(scanners > 0, "no scanner modules found; the guard would pass vacuously");
    }
}
