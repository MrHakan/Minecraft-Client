package me.mrhakan.agalarhack.services;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The early-game recipes an automated grind walks through, and the names it counts them under.
 *
 * <p>Two decisions are worth stating. First, the book is written in <strong>generic</strong> names -
 * "log", "planks" - rather than item ids, because a grind does not care whether the wood is oak or
 * birch and a book keyed by item id would need forty entries to say so. {@link #generic} maps a real
 * id onto those names, so counting an inventory and reading a recipe speak the same language.
 *
 * <p>Second, it is deliberately small. It covers the path from bare hands to an iron pickaxe, which
 * is the part of a run that is identical every time. Everything past that - armour, the nether,
 * trading - depends on what the world gives you and is better decided by a task that can look than
 * by a table written in advance.
 *
 * <p>Free of Minecraft types, so both the mapping and the recipes are unit tested.
 */
public final class GrindBook {
    private GrindBook() { }

    public static final String LOG = "log";
    public static final String PLANKS = "planks";
    public static final String STICK = "stick";
    public static final String CRAFTING_TABLE = "crafting_table";
    public static final String COBBLESTONE = "cobblestone";
    public static final String COAL = "coal";
    public static final String RAW_IRON = "raw_iron";
    public static final String IRON_INGOT = "iron_ingot";
    public static final String FURNACE = "furnace";
    public static final String WOODEN_PICKAXE = "wooden_pickaxe";
    public static final String STONE_PICKAXE = "stone_pickaxe";
    public static final String IRON_PICKAXE = "iron_pickaxe";

    private static final Map<String, CraftingPlan.Recipe> RECIPES = build();

    private static Map<String, CraftingPlan.Recipe> build() {
        Map<String, CraftingPlan.Recipe> book = new LinkedHashMap<>();
        book.put(PLANKS, new CraftingPlan.Recipe(PLANKS, 4, Map.of(LOG, 1), false));
        book.put(STICK, new CraftingPlan.Recipe(STICK, 4, Map.of(PLANKS, 2), false));
        book.put(CRAFTING_TABLE, new CraftingPlan.Recipe(CRAFTING_TABLE, 1, Map.of(PLANKS, 4), false));
        book.put(FURNACE, new CraftingPlan.Recipe(FURNACE, 1, Map.of(COBBLESTONE, 8), true));
        book.put(WOODEN_PICKAXE, new CraftingPlan.Recipe(WOODEN_PICKAXE, 1,
                Map.of(PLANKS, 3, STICK, 2), true));
        book.put(STONE_PICKAXE, new CraftingPlan.Recipe(STONE_PICKAXE, 1,
                Map.of(COBBLESTONE, 3, STICK, 2), true));
        book.put(IRON_PICKAXE, new CraftingPlan.Recipe(IRON_PICKAXE, 1,
                Map.of(IRON_INGOT, 3, STICK, 2), true));
        // Smelting rather than crafting, written at the size a furnace actually works in: one coal
        // burns for eight smelts. Charging a coal per ingot - which this did - is wrong by a factor
        // of eight, and the error grows with the goal: a full set of iron tools asked for over a
        // stack of coal to smelt what one eighth of that would. A batch of eight is exact whenever
        // the goal is a multiple of eight and rounds up to a furnace load below that, which is what
        // a player does anyway. Flagged as needing a station because a furnace is not a hand craft.
        book.put(IRON_INGOT, new CraftingPlan.Recipe(IRON_INGOT, 8, Map.of(RAW_IRON, 8, COAL, 1), true));
        return Map.copyOf(book);
    }

    /** The recipe book, for {@link CraftingPlan#plan}. */
    public static Map<String, CraftingPlan.Recipe> recipes() {
        return RECIPES;
    }

    public static boolean knows(String genericName) {
        return genericName != null && (RECIPES.containsKey(genericName) || isRaw(genericName));
    }

    /** Names with no recipe that a grind can still be asked for, so a typo is still refused. */
    private static boolean isRaw(String name) {
        return LOG.equals(name) || COBBLESTONE.equals(name) || COAL.equals(name)
                || RAW_IRON.equals(name);
    }

    /**
     * Maps a real item id onto the name this book counts it under.
     *
     * <p>Wood is the reason this exists: every plank counts as "planks" whatever tree it came from,
     * so a grind holding four birch planks is not sent to chop an oak. An id with no generic name
     * maps to itself, which keeps the planner honest about items this book has never heard of.
     */
    public static String generic(String itemId) {
        if (itemId == null || itemId.isBlank()) return "";
        String id = itemId.toLowerCase(Locale.ROOT);
        int colon = id.indexOf(':');
        if (colon >= 0) id = id.substring(colon + 1);

        if (id.endsWith("_planks")) return PLANKS;
        // Stripped variants and wood blocks all give planks, so they are all "log" to a grind.
        if (id.endsWith("_log") || id.endsWith("_wood")
                || id.endsWith("_stem") || id.endsWith("_hyphae")) return LOG;
        if (id.equals("stick")) return STICK;
        if (id.equals("cobblestone") || id.equals("cobbled_deepslate")) return COBBLESTONE;
        if (id.equals("coal") || id.equals("charcoal")) return COAL;
        return id;
    }
}
