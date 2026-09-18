package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeathWaypointsTest {
    private static final String OVERWORLD = "minecraft:overworld";

    private static Waypoint death(int x, int y, int z) {
        return new Waypoint(DeathWaypoints.nameFor(x, y, z), x, y, z, OVERWORLD, DeathWaypoints.COLOR, true, true);
    }

    @Test
    void namesCarryTheCoordinatesSoOneSpotIsOneEntry() {
        assertEquals(DeathWaypoints.nameFor(10, 64, -20), DeathWaypoints.nameFor(10, 64, -20));
        assertTrue(DeathWaypoints.nameFor(10, 64, -20).contains("-20"));
        assertFalse(DeathWaypoints.nameFor(10, 64, -20).equals(DeathWaypoints.nameFor(11, 64, -20)));
    }

    @Test
    void nameStaysInsideTheWaypointNameLimit() {
        String name = DeathWaypoints.nameFor(-29_999_999, -2048, 29_999_999);
        assertTrue(name.length() <= Waypoint.MAX_NAME, "was " + name.length());
        // The record would truncate anyway, but a name truncated twice would not round-trip.
        assertEquals(name, new Waypoint(name, 0, 0, 0, OVERWORLD, 0, true, true).name());
    }

    @Test
    void recognisesItsOwnWaypointsAndNotHandMadeOnes() {
        assertTrue(DeathWaypoints.isDeathWaypoint(death(0, 64, 0)));
        assertFalse(DeathWaypoints.isDeathWaypoint(Waypoint.of("Base", 0, 64, 0, OVERWORLD)));
        assertFalse(DeathWaypoints.isDeathWaypoint(null));
    }

    @Test
    void renamingADeathWaypointProtectsItFromPruning() {
        Waypoint renamed = new Waypoint("Stash from that death", 5, 64, 5, OVERWORLD, DeathWaypoints.COLOR, true, true);
        assertFalse(DeathWaypoints.isDeathWaypoint(renamed));
        DeathWaypoints.Plan plan = DeathWaypoints.plan(List.of(renamed), 9, 70, 9, OVERWORLD, 1);
        assertEquals(List.of(), plan.remove());
    }

    @Test
    void firstDeathAddsWithoutRemovingAnything() {
        DeathWaypoints.Plan plan = DeathWaypoints.plan(List.of(), 1, 2, 3, OVERWORLD, 3);
        assertEquals(List.of(), plan.remove());
        assertEquals(1, plan.add().x());
        assertEquals(3, plan.add().z());
        assertTrue(plan.add().beam());
        assertTrue(plan.add().visible());
    }

    @Test
    void prunesTheOldestDeathWaypointsOnceOverTheCap() {
        List<Waypoint> existing = List.of(death(0, 64, 0), death(1, 64, 1), death(2, 64, 2));
        DeathWaypoints.Plan plan = DeathWaypoints.plan(existing, 3, 64, 3, OVERWORLD, 3);
        assertEquals(List.of(death(0, 64, 0)), plan.remove(),
                "keeping 3 in total leaves room for 2 survivors beside the new one, so only the oldest goes");

        DeathWaypoints.Plan tighter = DeathWaypoints.plan(existing, 3, 64, 3, OVERWORLD, 2);
        assertEquals(List.of(death(0, 64, 0), death(1, 64, 1)), tighter.remove());
    }

    @Test
    void neverPrunesHandMadeWaypoints() {
        List<Waypoint> existing = List.of(
                Waypoint.of("Base", 0, 64, 0, OVERWORLD), death(1, 64, 1),
                Waypoint.of("Portal", 2, 64, 2, OVERWORLD), death(3, 64, 3));
        DeathWaypoints.Plan plan = DeathWaypoints.plan(existing, 9, 64, 9, OVERWORLD, 1);
        assertEquals(List.of(death(1, 64, 1), death(3, 64, 3)), plan.remove());
    }

    @Test
    void dyingTwiceAtTheSameSpotReplacesRatherThanAccumulates() {
        List<Waypoint> existing = List.of(death(5, 64, 5));
        DeathWaypoints.Plan plan = DeathWaypoints.plan(existing, 5, 64, 5, OVERWORLD, 1);
        assertEquals(List.of(), plan.remove(), "the entry being overwritten must not also be deleted");
        assertEquals(existing.get(0).key(), plan.add().key());
    }

    @Test
    void deathsInOtherDimensionsStillCountTowardsTheCap() {
        List<Waypoint> existing = List.of(
                new Waypoint(DeathWaypoints.nameFor(1, 64, 1), 1, 64, 1, "minecraft:the_nether",
                        DeathWaypoints.COLOR, true, true));
        DeathWaypoints.Plan plan = DeathWaypoints.plan(existing, 9, 64, 9, OVERWORLD, 1);
        assertEquals(1, plan.remove().size(),
                "otherwise the cap is per dimension and a nether trip hides every overworld death");
    }

    @Test
    void clampsTheKeepSetting() {
        List<Waypoint> existing = new ArrayList<>();
        for (int index = 0; index < DeathWaypoints.MAX_KEEP + 4; index++) existing.add(death(index, 64, index));
        assertEquals(existing.size(), DeathWaypoints.plan(existing, 99, 64, 99, OVERWORLD, 0).remove().size(),
                "keep 0 must behave as keep 1, not as keep everything");
        assertEquals(existing.size() - (DeathWaypoints.MAX_KEEP - 1),
                DeathWaypoints.plan(existing, 99, 64, 99, OVERWORLD, 9999).remove().size());
    }
}
