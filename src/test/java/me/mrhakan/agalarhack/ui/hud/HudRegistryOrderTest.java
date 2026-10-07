package me.mrhakan.agalarhack.ui.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import me.mrhakan.agalarhack.managers.HudLayoutManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HudRegistryOrderTest {
    @TempDir Path config;

    /** Loaded the way the client does at startup; saving stays disabled until a read succeeds. */
    private HudLayoutManager layout() {
        var layout = new HudLayoutManager(config);
        layout.load();
        return layout;
    }

    private static void register(HudRegistry registry, String id) {
        registry.register(new HudRegistry.Component(id, id, () -> 10, () -> 10, event -> { }),
                new HudLayoutManager.WidgetState(HudLayoutManager.Anchor.TOP_LEFT, 2, 2, true));
    }

    @Test
    void equalZOrderKeepsRegistrationOrder() {
        var registry = new HudRegistry(layout());
        register(registry, "alpha");
        register(registry, "beta");
        register(registry, "gamma");

        assertEquals(List.of("alpha", "beta", "gamma"), registry.ids());
    }

    @Test
    void anUnchangedLayoutReusesTheSameOrder() {
        // The point of the cache: the frame loop must not sort and allocate a list each time.
        var registry = new HudRegistry(layout());
        register(registry, "alpha");
        register(registry, "beta");

        assertSame(registry.ids(), registry.ids());
    }

    @Test
    void editingZOrderInPlaceIsPickedUpOnTheNextLookup() {
        // The HUD editor writes zOrder straight into the widget state with no change event.
        var layout = layout();
        var registry = new HudRegistry(layout);
        register(registry, "alpha");
        register(registry, "beta");
        register(registry, "gamma");
        registry.ids();

        layout.get("alpha").zOrder = 5;
        layout.get("gamma").zOrder = -1;

        assertEquals(List.of("gamma", "beta", "alpha"), registry.ids());
    }

    @Test
    void replacingTheLayoutWholesaleIsPickedUpToo() {
        // A profile or layout load swaps in new widget states rather than mutating the old ones.
        var layout = layout();
        var registry = new HudRegistry(layout);
        register(registry, "alpha");
        register(registry, "beta");
        registry.ids();

        var raised = new HudLayoutManager.WidgetState(HudLayoutManager.Anchor.TOP_LEFT, 2, 2, true);
        raised.zOrder = 9;
        layout.applySnapshot(Map.of("alpha", raised));

        assertEquals(List.of("beta", "alpha"), registry.ids());
    }

    @Test
    void aLateRegistrationJoinsTheOrder() {
        // Addons register widgets after the built-in HUD has already drawn frames.
        var registry = new HudRegistry(layout());
        register(registry, "alpha");
        registry.ids();
        register(registry, "addon.widget");

        assertEquals(List.of("alpha", "addon.widget"), registry.ids());
    }

    @Test
    void theOrderCannotBeModifiedByACaller() {
        var registry = new HudRegistry(layout());
        register(registry, "alpha");

        assertThrows(UnsupportedOperationException.class, () -> registry.ids().add("intruder"));
    }
}
