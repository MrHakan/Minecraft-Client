package me.mrhakan.agalarhack.ui.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the draw order the overlays had when they were one method, before the 2.0.03 split.
 *
 * <p>Name tags are submitted in list order and every overlay's lines share one buffer in list order,
 * so reordering the list changes what is drawn over what. That should be a decision, not an accident
 * of where someone added a line.
 */
class WorldOverlaysTest {
    @Test
    void linesAreDrawnInTheOriginalOrder() {
        List<String> drawn = WorldOverlays.overlays().stream()
                .filter(WorldOverlay::hasLines).map(WorldOverlay::moduleName).toList();
        assertEquals(List.of("ESP", "StorageESP", "BlockESP", "Trajectories", "SpawnESP", "HoleESP",
                "ProjectileESP", "Tracers", "Breadcrumbs", "ItemESP", "Waypoints", "Freecam"), drawn);
    }

    @Test
    void labelsAreSubmittedInTheOriginalOrder() {
        List<String> labelled = WorldOverlays.overlays().stream()
                .filter(WorldOverlay::hasLabels).map(WorldOverlay::moduleName).toList();
        assertEquals(List.of("ESP", "StorageESP", "Nametags", "ItemESP", "Waypoints"), labelled);
    }

    @Test
    void eachModuleHasOneOverlay() {
        List<String> names = WorldOverlays.overlays().stream().map(WorldOverlay::moduleName).toList();
        assertEquals(names.size(), names.stream().distinct().count(), "a module listed twice: " + names);
    }
}
