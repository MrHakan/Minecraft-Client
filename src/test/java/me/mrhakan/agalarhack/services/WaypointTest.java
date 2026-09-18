package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaypointTest {
    @Test void namesAreTrimmedAndInnerWhitespaceCollapsed() {
        assertEquals("my base", Waypoint.of("  my   base  ", 0, 0, 0, null).name());
    }

    @Test void namesAreBounded() {
        String long_ = "x".repeat(100);
        assertEquals(Waypoint.MAX_NAME, Waypoint.of(long_, 0, 0, 0, null).name().length());
    }

    @Test void anEmptyNameIsRejectedRatherThanStored() {
        assertThrows(IllegalArgumentException.class, () -> Waypoint.of("   ", 0, 0, 0, null));
        assertThrows(IllegalArgumentException.class, () -> Waypoint.of(null, 0, 0, 0, null));
    }

    @Test void coordinatesAreClampedToTheWorldLimits() {
        var point = Waypoint.of("far", Integer.MAX_VALUE, 99999, Integer.MIN_VALUE, null);
        assertEquals(Waypoint.MAX_HORIZONTAL, point.x());
        assertEquals(Waypoint.MAX_Y, point.y());
        assertEquals(-Waypoint.MAX_HORIZONTAL, point.z());
    }

    @Test void dimensionsAreCanonicalisedAndDefaulted() {
        assertEquals("minecraft:overworld", Waypoint.of("a", 0, 0, 0, null).dimension());
        assertEquals("minecraft:the_nether", Waypoint.of("a", 0, 0, 0, "the_nether").dimension());
        assertEquals("minecraft:the_end", Waypoint.of("a", 0, 0, 0, "MINECRAFT:THE_END").dimension());
        assertEquals("minecraft:overworld", Waypoint.of("a", 0, 0, 0, "not a dimension!").dimension());
    }

    @Test void coloursAreForcedOpaqueSoAWaypointCannotBeInvisibleByAccident() {
        assertEquals(0xFF123456, Waypoint.of("a", 0, 0, 0, null).withColor(0x00123456).color());
        assertEquals(0xFFFFFFFF, Waypoint.of("a", 0, 0, 0, null).withColor(0xFFFFFF).color());
    }

    @Test void identityIsCaseInsensitiveWithinADimension() {
        var lower = Waypoint.of("Base", 0, 0, 0, "overworld");
        var upper = Waypoint.of("BASE", 9, 9, 9, "overworld");
        var nether = Waypoint.of("Base", 0, 0, 0, "the_nether");
        assertEquals(lower.key(), upper.key());
        assertNotEquals(lower.key(), nether.key());
    }

    @Test void horizontalDistanceIgnoresHeight() {
        var point = new Waypoint("a", 3, 200, 4, null, Waypoint.DEFAULT_COLOR, true, false);
        assertEquals(5.0, point.horizontalDistanceTo(0.5, 0.5), 1e-9);
    }

    @Test void withersReturnNewValuesAndKeepTheRest() {
        var point = Waypoint.of("a", 1, 2, 3, "the_end");
        var hidden = point.withVisible(false).withBeam(true);
        assertFalse(hidden.visible());
        assertTrue(hidden.beam());
        assertEquals(1, hidden.x());
        assertEquals("minecraft:the_end", hidden.dimension());
        assertTrue(point.visible());
    }
}
