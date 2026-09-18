package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import org.junit.jupiter.api.Test;

class ModuleGuardTest {
    private final List<String> reports = new ArrayList<>();
    private final ModuleGuard guard = new ModuleGuard((module, failure) -> reports.add(module.getName()));

    private static Module module(String name) {
        return new Module(name, Category.MISC, "test");
    }

    @Test
    void workThatSucceedsIsReportedAsSuccess() {
        assertTrue(guard.run(module("A"), () -> { }));
        assertEquals(List.of(), reports);
    }

    @Test
    void aFailureIsContainedAndReported() {
        Module broken = module("Broken");
        assertFalse(guard.run(broken, () -> { throw new IllegalStateException("boom"); }));
        assertEquals(List.of("Broken"), reports);
        assertTrue(guard.isSuppressed(broken));
    }

    @Test
    void oneModuleFailingDoesNotStopTheOthers() {
        List<String> ran = new ArrayList<>();
        guard.run(module("First"), () -> ran.add("First"));
        guard.run(module("Broken"), () -> { throw new IllegalStateException("boom"); });
        guard.run(module("Third"), () -> ran.add("Third"));
        assertEquals(List.of("First", "Third"), ran,
                "a shared callback must not lose its other modules to one bad one");
    }

    @Test
    void repeatedFailuresAreReportedOnce() {
        Module broken = module("Broken");
        for (int tick = 0; tick < 100; tick++) {
            guard.run(broken, () -> { throw new IllegalStateException("boom"); });
        }
        assertEquals(1, reports.size(), "a per-tick notification buries the first report");
    }

    @Test
    void recoveringMakesTheNextFailureNewsAgain() {
        Module flaky = module("Flaky");
        guard.run(flaky, () -> { throw new IllegalStateException("boom"); });
        guard.run(flaky, () -> { });
        assertFalse(guard.isSuppressed(flaky));
        guard.run(flaky, () -> { throw new IllegalStateException("boom"); });
        assertEquals(2, reports.size());
    }

    @Test
    void modulesAreSuppressedIndependently() {
        Module first = module("First");
        Module second = module("Second");
        guard.run(first, () -> { throw new IllegalStateException("boom"); });
        guard.run(second, () -> { throw new IllegalStateException("boom"); });
        assertEquals(List.of("First", "Second"), reports);
    }

    @Test
    void aBrokenReporterCannotEscape() {
        ModuleGuard fragile = new ModuleGuard((module, failure) -> { throw new IllegalStateException("reporter"); });
        assertFalse(fragile.run(module("Broken"), () -> { throw new IllegalStateException("boom"); }),
                "the reporter throwing must not propagate out of the guard");
    }

    @Test
    void nullArgumentsAreRefusedRatherThanThrowing() {
        assertFalse(guard.run(null, () -> { }));
        assertFalse(guard.run(module("A"), null));
        assertEquals(List.of(), reports);
    }

    @Test
    void clearForgetsTheSuppression() {
        Module broken = module("Broken");
        guard.run(broken, () -> { throw new IllegalStateException("boom"); });
        guard.clear();
        assertFalse(guard.isSuppressed(broken));
        guard.run(broken, () -> { throw new IllegalStateException("boom"); });
        assertEquals(2, reports.size());
    }
}
