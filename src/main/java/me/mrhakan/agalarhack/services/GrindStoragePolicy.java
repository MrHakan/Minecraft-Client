package me.mrhakan.agalarhack.services;

/** Three single chests: building materials, minerals, and other surplus. Keep usable supplies carried. */
public final class GrindStoragePolicy {
    private GrindStoragePolicy() { }
    public static int category(String item) {
        if (item.equals("log") || item.equals("planks") || item.equals("cobblestone") || item.equals("netherrack")) return 0;
        if (item.contains("ingot") || item.contains("scrap") || item.equals("diamond") || item.equals("coal")
                || item.startsWith("raw_") || item.equals("lapis_lazuli") || item.equals("ancient_debris")) return 1;
        return 2;
    }
    public static int reserve(String item) {
        if (item.endsWith("_pickaxe") || item.endsWith("_axe") || item.endsWith("_shovel") || item.endsWith("_sword")
                || item.endsWith("_helmet") || item.endsWith("_chestplate") || item.endsWith("_leggings") || item.endsWith("_boots")
                || item.equals("shield") || item.contains("bucket") || item.contains("template")
                || item.equals("flint_and_steel")) return Integer.MAX_VALUE;
        return switch (item) {
            case "log", "planks", "cobblestone", "torch" -> 32;
            case "coal", "iron_ingot", "diamond", "lapis_lazuli" -> 16;
            case "bread", "cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton" -> 24;
            case "crafting_table", "furnace", "chest" -> 1;
            default -> 8;
        };
    }
}
