package me.mrhakan.agalarhack.services;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Vanilla survival recipes and resource vocabulary, independent of Minecraft runtime types. */
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

    public static final Set<String> RAW = Set.of(LOG, COBBLESTONE, COAL, RAW_IRON,
            "diamond", "raw_gold", "ancient_debris", "obsidian", "flint", "wheat", "sugar_cane",
            "leather", "beef", "porkchop", "chicken", "mutton", "lapis_lazuli",
            "netherite_upgrade_smithing_template", "netherrack");
    private static final Map<String, String> SMELTS = Map.of(
            IRON_INGOT, RAW_IRON, "gold_ingot", "raw_gold", "netherite_scrap", "ancient_debris",
            "cooked_beef", "beef", "cooked_porkchop", "porkchop", "cooked_chicken", "chicken",
            "cooked_mutton", "mutton");

    public static boolean smelted(String item) { return SMELTS.containsKey(item); }
    public static String smeltInput(String item) { return SMELTS.get(item); }
    public static boolean smithing(String item) { return item != null && item.startsWith("netherite_")
            && !item.equals("netherite_ingot") && !item.equals("netherite_scrap")
            && !item.equals("netherite_upgrade_smithing_template"); }

    private static final Map<String, CraftingPlan.Recipe> RECIPES = build();

    private static final Map<String, String[]> BARITONE_TARGETS = Map.ofEntries(
            Map.entry(LOG, new String[]{
                    "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log",
                    "mangrove_log", "cherry_log", "pale_oak_log",
                    "oak_wood", "spruce_wood", "birch_wood", "jungle_wood", "acacia_wood", "dark_oak_wood",
                    "mangrove_wood", "cherry_wood", "pale_oak_wood",
                    "crimson_stem", "warped_stem", "crimson_hyphae", "warped_hyphae"
            }),
            Map.entry(COBBLESTONE, new String[]{"stone", "deepslate"}),
            Map.entry(COAL, new String[]{"coal_ore", "deepslate_coal_ore"}),
            Map.entry(RAW_IRON, new String[]{"iron_ore", "deepslate_iron_ore"}),
            Map.entry("diamond", new String[]{"diamond_ore", "deepslate_diamond_ore"}),
            Map.entry("raw_gold", new String[]{"gold_ore", "deepslate_gold_ore"}),
            Map.entry("ancient_debris", new String[]{"ancient_debris"}),
            Map.entry("obsidian", new String[]{"obsidian"}),
            Map.entry("lapis_lazuli", new String[]{"lapis_ore", "deepslate_lapis_ore"}),
            Map.entry("netherrack", new String[]{"netherrack"}),
            Map.entry("sugar_cane", new String[]{"sugar_cane"})
    );

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
        for (String material : new String[]{"stone", "iron", "diamond"}) {
            String ingredient = material.equals("stone") ? COBBLESTONE : material.equals("iron") ? IRON_INGOT : "diamond";
            for (String tool : new String[]{"pickaxe", "axe", "shovel", "sword"}) {
                int head = tool.equals("shovel") ? 1 : tool.equals("sword") ? 2 : 3;
                book.put(material + "_" + tool, new CraftingPlan.Recipe(material + "_" + tool, 1,
                        Map.of(ingredient, head, STICK, tool.equals("sword") ? 1 : 2), true));
            }
            if (!material.equals("stone")) {
                for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
                    int cost = switch (piece) { case "helmet" -> 5; case "chestplate" -> 8; case "leggings" -> 7; default -> 4; };
                    book.put(material + "_" + piece, new CraftingPlan.Recipe(material + "_" + piece, 1, Map.of(ingredient, cost), true));
                }
            }
        }
        book.put("shield", new CraftingPlan.Recipe("shield", 1, Map.of(PLANKS, 6, IRON_INGOT, 1), true));
        book.put("bucket", new CraftingPlan.Recipe("bucket", 1, Map.of(IRON_INGOT, 3), true));
        book.put("torch", new CraftingPlan.Recipe("torch", 4, Map.of(COAL, 1, STICK, 1), false));
        book.put("chest", new CraftingPlan.Recipe("chest", 1, Map.of(PLANKS, 8), true));
        book.put("oak_door", new CraftingPlan.Recipe("oak_door", 3, Map.of("oak_planks", 6), true));
        // Generic planks can be mixed for tools/chests, but a wood-specific door cannot.
        book.put("oak_planks", new CraftingPlan.Recipe("oak_planks", 4, Map.of("oak_log", 1), false));
        book.put("bread", new CraftingPlan.Recipe("bread", 1, Map.of("wheat", 3), true));
        book.put("paper", new CraftingPlan.Recipe("paper", 3, Map.of("sugar_cane", 3), true));
        book.put("book", new CraftingPlan.Recipe("book", 1, Map.of("paper", 3, "leather", 1), false));
        book.put("bookshelf", new CraftingPlan.Recipe("bookshelf", 1, Map.of(PLANKS, 6, "book", 3), true));
        book.put("enchanting_table", new CraftingPlan.Recipe("enchanting_table", 1,
                Map.of("obsidian", 4, "diamond", 2, "book", 1), true));
        book.put("smithing_table", new CraftingPlan.Recipe("smithing_table", 1, Map.of(PLANKS, 4, IRON_INGOT, 2), true));
        book.put("flint_and_steel", new CraftingPlan.Recipe("flint_and_steel", 1, Map.of("flint", 1, IRON_INGOT, 1), false));
        SMELTS.forEach((output, input) -> {
            if (!output.equals(IRON_INGOT)) book.put(output, new CraftingPlan.Recipe(output, 8, Map.of(input, 8, COAL, 1), true));
        });
        book.put("netherite_ingot", new CraftingPlan.Recipe("netherite_ingot", 1,
                Map.of("netherite_scrap", 4, "gold_ingot", 4), true));
        for (String part : new String[]{"pickaxe", "axe", "shovel", "sword", "helmet", "chestplate", "leggings", "boots"}) {
            String output = "netherite_" + part;
            book.put(output, new CraftingPlan.Recipe(output, 1,
                    Map.of("diamond_" + part, 1, "netherite_ingot", 1, "netherite_upgrade_smithing_template", 1), true));
        }
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
        return RAW.contains(name) || "oak_log".equals(name);
    }

    /**
     * Returns the real block names accepted by Baritone for a raw resource goal.
     *
     * <p>A copy is returned so callers cannot mutate the shared recipe vocabulary. Crafted goals
     * intentionally return an empty array: the executor must not pretend that mining alone can make
     * a pickaxe, furnace or other crafted item.
     */
    public static String[] baritoneNames(String genericName) {
        if ("oak_log".equals(genericName)) return new String[]{"oak_log"};
        String normalized = generic(genericName);
        String[] names = BARITONE_TARGETS.get(normalized);
        return names == null ? new String[0] : names.clone();
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
