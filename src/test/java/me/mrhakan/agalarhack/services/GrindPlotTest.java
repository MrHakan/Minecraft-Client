package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GrindPlotTest {
    private static final int FEET = 64;
    private static final List<SurvivalBlueprint.Column> IRON = SurvivalBlueprint.footprint(SurvivalProgression.Tier.IRON);

    /** Level stone at FEET - 1 everywhere, with chosen columns raised, lowered, flooded or unloaded. */
    private static final class Field implements GrindPlot.Terrain {
        final Map<Long, Integer> tops = new HashMap<>();
        final Set<Long> fluid = new HashSet<>();
        int defaultTop = FEET - 1;
        static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }
        Field set(int x, int z, int top) { tops.put(key(x, z), top); return this; }
        Field pillars(int fromX, int toX, int fromZ, int toZ, int every) {
            for (int x = fromX; x <= toX; x++) for (int z = fromZ; z <= toZ; z++)
                if (Math.floorMod(x, every) == 0 && Math.floorMod(z, every) == 0) set(x, z, FEET + 2);
            return this;
        }
        public int top(int x, int z) { return tops.getOrDefault(key(x, z), defaultTop); }
        public boolean solid(int x, int z) { return !fluid.contains(key(x, z)); }
    }

    @Test
    void levelGroundKeepsTheDefaultCorner() {
        assertEquals(new GrindPlot.Plot(2, FEET, 0), GrindPlot.choose(new Field(), IRON, 2, FEET, 0));
    }

    @Test
    void anObstructedStartMovesToTheNearestPlotThatFits() {
        // A single pillar inside the default footprint; the nearest corner whose footprint avoids it wins.
        Field field = new Field().set(4, 2, FEET + 1);
        GrindPlot.Plot plot = GrindPlot.choose(field, IRON, 2, FEET, 0);
        assertNotNull(plot);
        for (SurvivalBlueprint.Column column : IRON)
            assertTrue(plot.x() + column.x() != 4 || plot.z() + column.z() != 2, "the chosen plot covers the pillar");
        // The footprint is five wide, so the nearest corner that clears x = 4 is three blocks west.
        assertEquals(new GrindPlot.Plot(-1, FEET, 0), plot);
    }

    @Test
    void leavesWaterAndUnloadedChunksDoNotFit() {
        Field leaves = new Field().set(2, 1, FEET);  // anything at floor level, a leaf included
        assertEquals(null, GrindPlot.floor(leaves, IRON, 2, FEET, 0));
        Field water = new Field();
        water.fluid.add(Field.key(3, 3));
        assertEquals(null, GrindPlot.floor(water, IRON, 2, FEET, 0));
        Field unloaded = new Field().set(5, 0, GrindPlot.UNKNOWN);
        assertEquals(null, GrindPlot.floor(unloaded, IRON, 2, FEET, 0));
    }

    @Test
    void smallDipsAreBridgedButHolesAreNot() {
        Field dips = new Field().set(3, 1, FEET - 2).set(4, 3, FEET - 3);
        assertEquals(FEET, GrindPlot.floor(dips, IRON, 2, FEET, 0));
        Field hole = new Field().set(3, 1, FEET - 4);
        assertEquals(null, GrindPlot.floor(hole, IRON, 2, FEET, 0));
    }

    @Test
    void mostOfTheFloorMustRestOnGround() {
        Field sunken = new Field();
        // Lower every column but the corner one block: level, but nearly all of it unsupported.
        for (SurvivalBlueprint.Column column : IRON)
            if (column.x() != 0 || column.z() != 0) sunken.set(2 + column.x(), column.z(), FEET - 2);
        assertEquals(null, GrindPlot.floor(sunken, IRON, 2, FEET, 0));
    }

    @Test
    void aPlotOnHigherOrLowerGroundUsesThatFloor() {
        Field terrace = new Field();
        terrace.defaultTop = FEET + 2;
        assertEquals(new GrindPlot.Plot(2, FEET + 3, 0), GrindPlot.choose(terrace, IRON, 2, FEET, 0));
        terrace.defaultTop = FEET + GrindPlot.HIGHEST_FLOOR;
        assertNull(GrindPlot.choose(terrace, IRON, 2, FEET, 0), "a floor above the scan window's reach is refused");
    }

    @Test
    void noPlotWithinTheRadiusIsReported() {
        int reach = GrindPlot.SEARCH_RADIUS + 20;
        Field crowded = new Field().pillars(-reach, reach, -reach, reach, 3);
        assertNull(GrindPlot.choose(crowded, IRON, 2, FEET, 0));
    }

    @Test
    void theSameTerrainAlwaysGivesTheSamePlot() {
        Field field = new Field().pillars(-6, 10, -6, 6, 4);
        assertEquals(GrindPlot.choose(field, IRON, 2, FEET, 0), GrindPlot.choose(field, IRON, 2, FEET, 0));
    }

    @Test
    void eachTierCoversItsOwnBuildings() {
        var iron = Set.copyOf(IRON);
        var diamond = Set.copyOf(SurvivalBlueprint.footprint(SurvivalProgression.Tier.DIAMOND));
        var max = Set.copyOf(SurvivalBlueprint.footprint(SurvivalProgression.Tier.MAX));
        assertTrue(diamond.containsAll(iron) && max.containsAll(diamond));
        for (var p : SurvivalBlueprint.house(false)) assertTrue(iron.contains(new SurvivalBlueprint.Column(p.x(), p.z())));
        for (var p : SurvivalBlueprint.house(true)) assertTrue(diamond.contains(new SurvivalBlueprint.Column(p.x(), p.z())));
        for (var p : SurvivalBlueprint.portal())
            assertTrue(max.contains(new SurvivalBlueprint.Column(SurvivalBlueprint.PORTAL_X + p.x(), p.z())));
        // The gap between the enchanting table and its shelves must be clear too.
        assertTrue(max.contains(new SurvivalBlueprint.Column(SurvivalBlueprint.ENCHANTING_X + 1, SurvivalBlueprint.ENCHANTING_Z)));
        assertEquals(1 + 15, SurvivalBlueprint.enchantingArea().size(), "one table and fifteen shelves");
    }
}
