package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;

/** Relative, ordered placements. Floor, walls, roof, then fixtures; no terrain is erased. */
public final class SurvivalBlueprint {
    private SurvivalBlueprint() { }
    public record Placement(int x, int y, int z, String item) { }
    /** One ground column, relative to the base corner. */
    public record Column(int x, int z) { }

    /** Where the portal frame and the enchanting area stand relative to the base corner. */
    static final int PORTAL_X = -5;
    static final int ENCHANTING_X = 8, ENCHANTING_Z = 10;
    /** The enchanting area is the table, a ring of shelves two out, and the one-block gap between. */
    static final int ENCHANTING_REACH = 2;
    public static List<Placement> house(boolean expanded) {
        int size = expanded ? 7 : 5, offset = expanded ? 6 : 0;
        List<Placement> blocks = new ArrayList<>();
        // A raised floor keeps the entire build above the selected terrain level.
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++)
            blocks.add(new Placement(x + offset, 0, z, "cobblestone"));
        for (int y = 1; y <= 3; y++) for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
            if (x != 0 && z != 0 && x != size - 1 && z != size - 1) continue;
            if (z == 0 && x == size / 2 && y <= 2) continue;
            blocks.add(new Placement(x + offset, y, z, "planks"));
        }
        // Build roof from the perimeter inward so every roof cell has an adjacent support.
        for (int ring = 0; ring <= size / 2; ring++) for (int x = ring; x < size - ring; x++)
            for (int z = ring; z < size - ring; z++) {
                if (x == ring || z == ring || x == size - ring - 1 || z == size - ring - 1)
                    blocks.add(new Placement(x + offset, 4, z, "cobblestone"));
            }
        blocks.add(new Placement(size / 2 + offset, 1, 0, "oak_door"));
        blocks.add(new Placement(1 + offset, 1, 1, "torch"));
        blocks.add(new Placement(size - 2 + offset, 1, expanded ? 1 : size - 2, "torch"));
        // Independent single chests: avoid menu-layout ambiguity from accidental double chests.
        blocks.add(new Placement(1 + offset, 1, size - 2, "chest"));
        if (expanded) {
            blocks.add(new Placement(3 + offset, 1, size - 2, "chest"));
            blocks.add(new Placement(5 + offset, 1, size - 2, "chest"));
            for (int x = 2; x <= offset + size / 2; x++) blocks.add(new Placement(x, 0, -1, "cobblestone"));
        } else {
            blocks.add(new Placement(3, 1, 1, "crafting_table"));
            blocks.add(new Placement(3, 1, 2, "furnace"));
            blocks.add(new Placement(2, 0, -1, "cobblestone"));
        }
        return List.copyOf(blocks);
    }
    public static Map<String, Integer> materials(boolean expanded) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Placement placement : house(expanded)) result.merge(placement.item(), 1, Integer::sum);
        return Map.copyOf(result);
    }
    /** The table at the centre and fifteen shelves on the ring two blocks out, leaving the front open. */
    public static List<Placement> enchantingArea() {
        List<Placement> layout = new ArrayList<>();
        layout.add(new Placement(0, 0, 0, "enchanting_table"));
        for (int x = -ENCHANTING_REACH; x <= ENCHANTING_REACH; x++) for (int z = -ENCHANTING_REACH; z <= ENCHANTING_REACH; z++) {
            if (Math.abs(x) != ENCHANTING_REACH && Math.abs(z) != ENCHANTING_REACH || x == 0 && z == -ENCHANTING_REACH) continue;
            layout.add(new Placement(x, 0, z, "bookshelf"));
        }
        return List.copyOf(layout);
    }

    /**
     * Every ground column a tier's buildings stand on, relative to the base corner: the houses, the
     * portal frame and the whole enchanting area, whose gap must stay clear for the shelves to count.
     * Storage chests stand inside the houses.
     */
    public static List<Column> footprint(SurvivalProgression.Tier tier) {
        Set<Column> columns = new TreeSet<>(Comparator.comparingInt(Column::x).thenComparingInt(Column::z));
        for (SurvivalProgression.Goal goal : SurvivalProgression.goals(tier)) {
            switch (goal.kind()) {
                case HOUSE -> house(goal.count() > 1).forEach(p -> columns.add(new Column(p.x(), p.z())));
                case PORTAL -> portal().forEach(p -> columns.add(new Column(PORTAL_X + p.x(), p.z())));
                case ENCHANT -> {
                    for (int x = -ENCHANTING_REACH; x <= ENCHANTING_REACH; x++) for (int z = -ENCHANTING_REACH; z <= ENCHANTING_REACH; z++)
                        columns.add(new Column(ENCHANTING_X + x, ENCHANTING_Z + z));
                }
                default -> { }
            }
        }
        return List.copyOf(columns);
    }

    public static List<Placement> portal() {
        List<Placement> result = new ArrayList<>();
        // Four-wide, five-high frame with corners for direct vanilla placement support.
        for (int y = 0; y <= 4; y++) for (int x = 0; x <= 3; x++) {
            if (y == 0 || y == 4 || x == 0 || x == 3)
                result.add(new Placement(x, y, 0, "obsidian"));
        }
        return List.copyOf(result);
    }
}
