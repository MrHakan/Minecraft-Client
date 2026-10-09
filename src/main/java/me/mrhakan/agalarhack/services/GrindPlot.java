package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where the survival campaign builds: the nearest clear, level plot for every building its tier
 * will put up, searched once when a campaign starts in a new world.
 *
 * <p>A plot fits when, for every column of {@link SurvivalBlueprint#footprint}, nothing a build
 * could not replace (a block, leaves, a fluid) stands at or above floor level within the scan
 * window, the ground under the floor is solid and at most {@value #MAX_DIP} blocks low, and at least
 * {@value #SUPPORTED_PERCENT}% of the columns have it exactly one below the floor. A dip of a block or
 * two is bridged by the floor's neighbours as it is placed; a hill, a tree, water or a cliff is not.
 *
 * <p>The corner the player would get without a search is tried first, so starting on open level
 * ground builds exactly where it always did. Kept free of Minecraft types: the terrain arrives
 * through {@link Terrain}, one column at a time.
 */
final class GrindPlot {
    private GrindPlot() { }

    /** Horizontal distance from the default corner searched for a plot. */
    static final int SEARCH_RADIUS = 16;
    /** Floor levels tried, relative to the player's feet. */
    static final int LOWEST_FLOOR = -6, HIGHEST_FLOOR = 4;
    /**
     * The window each column is scanned in, relative to the player's feet: from five above the
     * highest floor tried, so a five-high build always lies inside it, to below the lowest.
     */
    static final int SCAN_TOP = HIGHEST_FLOOR + 4, SCAN_BOTTOM = LOWEST_FLOOR - 4;
    static final int MAX_DIP = 2;
    static final int SUPPORTED_PERCENT = 80;
    /** A column with no known top: unloaded, or open all the way down the window. */
    static final int UNKNOWN = Integer.MIN_VALUE;

    /** The terrain the search reads; implementations cache, because neighbouring plots share columns. */
    interface Terrain {
        /** The highest y in the scan window holding something a build could not replace, or {@link #UNKNOWN}. */
        int top(int x, int z);
        /** Whether that block can be built on: it has a collision shape and is not a fluid. */
        boolean solid(int x, int z);
    }

    /** The base corner: the floor of every building is laid at {@code y}. */
    record Plot(int x, int y, int z) { }

    /**
     * @param defaultX the corner a campaign uses without a search, which is tried first
     * @param feetY    the player's feet, which the scan window and floor levels are measured from
     * @return the nearest plot that fits, or {@code null} when none within {@link #SEARCH_RADIUS} does
     */
    static Plot choose(Terrain terrain, List<SurvivalBlueprint.Column> footprint, int defaultX, int feetY, int defaultZ) {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) offsets.add(new int[]{dx, dz});
        }
        // Nearest first, then a fixed order, so the same terrain always gives the same plot.
        offsets.sort(Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1])
                .thenComparingInt(o -> o[0]).thenComparingInt(o -> o[1]));
        for (int[] offset : offsets) {
            int x = defaultX + offset[0], z = defaultZ + offset[1];
            Integer floor = floor(terrain, footprint, x, feetY, z);
            if (floor != null) return new Plot(x, floor, z);
        }
        return null;
    }

    /** The floor level a plot with its corner at this column would have, or {@code null} if it does not fit. */
    static Integer floor(Terrain terrain, List<SurvivalBlueprint.Column> footprint, int x, int feetY, int z) {
        int corner = terrain.top(x, z);
        if (corner == UNKNOWN) return null;
        int floor = corner + 1;
        if (floor < feetY + LOWEST_FLOOR || floor > feetY + HIGHEST_FLOOR) return null;
        int supported = 0;
        for (SurvivalBlueprint.Column column : footprint) {
            int cx = x + column.x(), cz = z + column.z();
            int top = terrain.top(cx, cz);
            if (top == UNKNOWN || top >= floor || top < floor - 1 - MAX_DIP || !terrain.solid(cx, cz)) return null;
            if (top == floor - 1) supported++;
        }
        return supported * 100 >= SUPPORTED_PERCENT * footprint.size() ? floor : null;
    }
}
