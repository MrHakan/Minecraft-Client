package me.mrhakan.agalarhack.services;

import java.nio.file.Files;
import java.nio.file.Path;
import me.mrhakan.agalarhack.config.WaypointCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class WaypointServiceTest {
    private static WaypointService loaded(Path path) {
        var service = new WaypointService(path);
        service.load();
        return service;
    }

    @Test void addedWaypointsSurviveAReload(@TempDir Path dir) {
        Path file = dir.resolve("w.json");
        var service = loaded(file);
        assertTrue(service.add(Waypoint.of("home", 10, 64, 20, "overworld")));
        var reloaded = loaded(file);
        assertEquals(1, reloaded.count());
        assertEquals(10, reloaded.find("home", null).orElseThrow().x());
    }

    @Test void sameNameInTheSameDimensionReplacesRatherThanDuplicates(@TempDir Path dir) {
        var service = loaded(dir.resolve("w.json"));
        service.add(Waypoint.of("home", 1, 1, 1, "overworld"));
        service.add(Waypoint.of("HOME", 2, 2, 2, "overworld"));
        assertEquals(1, service.count());
        assertEquals(2, service.find("home", "minecraft:overworld").orElseThrow().x());
    }

    @Test void theSameNameInAnotherDimensionIsASeparateWaypoint(@TempDir Path dir) {
        var service = loaded(dir.resolve("w.json"));
        service.add(Waypoint.of("home", 1, 1, 1, "overworld"));
        service.add(Waypoint.of("home", 2, 2, 2, "the_nether"));
        assertEquals(2, service.count());
        assertEquals(2, service.find("home", "minecraft:the_nether").orElseThrow().x());
    }

    @Test void lookupPrefersTheCurrentDimensionThenFallsBackAnywhere(@TempDir Path dir) {
        var service = loaded(dir.resolve("w.json"));
        service.add(Waypoint.of("stash", 5, 5, 5, "the_end"));
        assertEquals(5, service.find("stash", "minecraft:overworld").orElseThrow().x());
    }

    @Test void visibleListIsScopedToOneDimension(@TempDir Path dir) {
        var service = loaded(dir.resolve("w.json"));
        service.add(Waypoint.of("a", 0, 0, 0, "overworld"));
        service.add(Waypoint.of("b", 0, 0, 0, "overworld").withVisible(false));
        service.add(Waypoint.of("c", 0, 0, 0, "the_nether"));
        var visible = service.visibleIn("minecraft:overworld");
        assertEquals(1, visible.size());
        assertEquals("a", visible.get(0).name());
    }

    @Test void clearCanBeScopedOrTotal(@TempDir Path dir) {
        var service = loaded(dir.resolve("w.json"));
        service.add(Waypoint.of("a", 0, 0, 0, "overworld"));
        service.add(Waypoint.of("b", 0, 0, 0, "the_nether"));
        assertEquals(1, service.clear("minecraft:overworld"));
        assertEquals(1, service.count());
        assertEquals(1, service.clear(null));
        assertEquals(0, service.count());
    }

    @Test void removalReportsWhetherAnythingMatched(@TempDir Path dir) {
        var service = loaded(dir.resolve("w.json"));
        service.add(Waypoint.of("a", 0, 0, 0, "overworld"));
        assertFalse(service.remove("missing", null));
        assertTrue(service.remove("A", null));
        assertEquals(0, service.count());
    }

    @Test void theStoreIsBounded(@TempDir Path dir) {
        var service = loaded(dir.resolve("w.json"));
        for (int i = 0; i < WaypointCodec.MAX_WAYPOINTS; i++) {
            assertTrue(service.add(Waypoint.of("w" + i, i, 0, 0, "overworld")));
        }
        assertFalse(service.add(Waypoint.of("overflow", 0, 0, 0, "overworld")));
        // Replacing an existing waypoint must still work at the limit.
        assertTrue(service.add(Waypoint.of("w0", 9, 9, 9, "overworld")));
        assertEquals(WaypointCodec.MAX_WAYPOINTS, service.count());
    }

    @Test void anUnreadableFileIsPreservedAndSavingStaysDisabled(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("w.json");
        Files.writeString(file, "{ this is not valid json");
        var service = loaded(file);
        assertEquals(0, service.count());
        service.add(Waypoint.of("new", 1, 1, 1, "overworld"));
        assertFalse(service.save());
        assertEquals("{ this is not valid json", Files.readString(file));
    }

    @Test void replaceOnlyUpdatesAnExistingWaypoint(@TempDir Path dir) {
        var service = loaded(dir.resolve("w.json"));
        var point = Waypoint.of("a", 0, 0, 0, "overworld");
        assertFalse(service.replace(point));
        service.add(point);
        assertTrue(service.replace(point.withBeam(true)));
        assertTrue(service.find("a", null).orElseThrow().beam());
    }
}
