package me.mrhakan.agalarhack.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HudLayoutManagerTest {
    @TempDir Path config;

    /** Loaded the way the client does at startup; saving stays disabled until a read succeeds. */
    private HudLayoutManager layout() {
        var layout = new HudLayoutManager(config);
        layout.load();
        return layout;
    }

    @Test
    void aPresentWidgetIsTheSameStoredStateOnEveryLookup() {
        // The editor moves a widget by writing to what get() returns, and render code calls get()
        // several times per widget per frame. Both rely on this being one stored object.
        var layout = layout();
        var modules = layout.get("modules");
        modules.offsetX = 41;

        assertSame(modules, layout.get("modules"));
        assertEquals(41, layout.get("modules").offsetX);
    }

    @Test
    void lookingUpOneWidgetDoesNotReplaceAnyOther() {
        // get() used to re-run ensureDefaults on every call; a regression there that copied instead
        // of filling gaps would swap out widgets the editor is holding mid-drag.
        var layout = layout();
        var branding = layout.get("branding");
        var target = layout.get("target");

        for (int call = 0; call < 50; call++) layout.get("modules");
        layout.get("never-declared");

        assertSame(branding, layout.get("branding"));
        assertSame(target, layout.get("target"));
    }

    @Test
    void declaredDefaultsKeepTheirOwnPlacement() {
        var layout = layout();

        assertEquals(HudLayoutManager.Anchor.TOP_RIGHT, layout.get("modules").anchor);
        assertEquals(HudLayoutManager.Anchor.BOTTOM_LEFT, layout.get("info").anchor);
        assertEquals(54, layout.get("target").offsetY);
    }

    @Test
    void anUndeclaredWidgetGetsTheGenericPlacementAndKeepsIt() {
        var layout = layout();
        var unknown = layout.get("addon.widget");

        assertEquals(HudLayoutManager.Anchor.TOP_LEFT, unknown.anchor);
        assertTrue(unknown.visible);
        assertSame(unknown, layout.get("addon.widget"));
    }

    @Test
    void aRegisteredDefaultIsCopiedNotShared() {
        var layout = layout();
        var declared = new HudLayoutManager.WidgetState(HudLayoutManager.Anchor.BOTTOM_RIGHT, 8, 28, false);
        layout.registerDefault("ping_graph", declared);

        var live = layout.get("ping_graph");
        assertNotSame(declared, live);
        assertEquals(HudLayoutManager.Anchor.BOTTOM_RIGHT, live.anchor);
        assertEquals(28, live.offsetY);

        // Moving the live widget must not move the default that reset() restores.
        live.offsetY = 300;
        layout.reset("ping_graph");
        assertEquals(28, layout.get("ping_graph").offsetY);
    }

    @Test
    void aSnapshotWithoutSomeWidgetsStillLeavesEveryDeclaredDefaultPresent() {
        var layout = layout();
        layout.registerDefault("inventory",
                new HudLayoutManager.WidgetState(HudLayoutManager.Anchor.BOTTOM_RIGHT, 8, 110, false));
        var moved = new HudLayoutManager.WidgetState(HudLayoutManager.Anchor.TOP_LEFT, 90, 90, true);

        layout.applySnapshot(Map.of("modules", moved));

        assertEquals(90, layout.get("modules").offsetX);
        assertEquals(HudLayoutManager.Anchor.BOTTOM_RIGHT, layout.get("inventory").anchor);
        assertEquals(110, layout.get("inventory").offsetY);
        assertTrue(layout.snapshot().containsKey("branding"));
    }

    @Test
    void savedLayoutsRoundTripThroughTheConfigDirectory() {
        var first = layout();
        first.get("modules").offsetX = 77;
        first.save();

        var second = new HudLayoutManager(config);
        second.load();
        assertEquals(77, second.get("modules").offsetX);
        assertTrue(config.resolve("agalarhack-hud.json").toFile().isFile());
    }
}
