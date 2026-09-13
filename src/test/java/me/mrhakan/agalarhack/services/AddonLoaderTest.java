package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The loader's own behaviour, minus the loader-provided discovery.
 *
 * <p>Finding addons is Fabric's job and needs a real loader, so that half is covered by the game
 * test, which declares a real entrypoint in the game test mod's own {@code fabric.mod.json} and
 * asserts the module and command it registers actually arrive. What is checked here is the part
 * that would be wrong in a way no entrypoint could reveal: that loading runs once, and that the
 * record it keeps distinguishes an addon that worked from one that threw.
 */
class AddonLoaderTest {
    @Test void loadingIsAttemptedOnlyOnce() {
        var loader = new AddonLoader();
        assertFalse(loader.ran());
        loader.load();
        assertTrue(loader.ran());
        int after = loader.loaded().size();
        loader.load();
        assertEquals(after, loader.loaded().size(), "a second call must not load anything twice");
    }

    /** With no Fabric loader behind it there are no addons, and that is not an error. */
    @Test void noAddonsIsNotAFailure() {
        var loader = new AddonLoader();
        loader.load();
        assertTrue(loader.loaded().isEmpty());
    }

    @Test void theRecordTellsAWorkingAddonFromABrokenOne() {
        var fine = new AddonLoader.Loaded("a", "A", "1.0", 2, 1, null);
        var broken = new AddonLoader.Loaded("b", "B", "1.0", 0, 0, "java.lang.IllegalStateException");
        assertTrue(fine.ok());
        assertFalse(broken.ok());
        assertEquals(2, fine.modules());
        assertEquals("java.lang.IllegalStateException", broken.failure());
    }
}
