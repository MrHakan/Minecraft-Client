package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Milestones are ordered, but each item goal is recompiled from live inventory at execution time. */
public final class SurvivalProgression {
    private SurvivalProgression() { }
    public enum Tier { IRON, DIAMOND, MAX;
        public static Tier parse(String value) { return valueOf(value.toUpperCase(Locale.ROOT)); }
    }
    public enum Kind { ITEM, FOOD, HOUSE, STORAGE, PORTAL, NETHER, OVERWORLD, ENCHANT }
    public record Goal(Kind kind, String item, int count) { }
    public static List<Goal> goals(Tier tier) {
        List<Goal> result = new ArrayList<>();
        item(result, "wooden_pickaxe", 1); item(result, "stone_pickaxe", 1);
        item(result, "stone_axe", 1); item(result, "stone_sword", 1);
        result.add(new Goal(Kind.FOOD, "food reserve", 8));
        item(result, "iron_pickaxe", 1); item(result, "shield", 1);
        gear(result, "iron"); item(result, "bucket", 1); item(result, "torch", 32);
        result.add(new Goal(Kind.HOUSE, "starter house", 1));
        result.add(new Goal(Kind.STORAGE, "starter storage", 1));
        if (tier == Tier.IRON) return List.copyOf(result);
        item(result, "diamond_pickaxe", 1); gear(result, "diamond");
        result.add(new Goal(Kind.FOOD, "food reserve", 16));
        result.add(new Goal(Kind.HOUSE, "storage annex", 2));
        result.add(new Goal(Kind.STORAGE, "sorted storage", 3));
        if (tier == Tier.DIAMOND) return List.copyOf(result);
        item(result, "diamond", 49); // Seven copies of the first looted template, seven diamonds each.
        item(result, "obsidian", 18); item(result, "flint_and_steel", 1);
        result.add(new Goal(Kind.PORTAL, "Nether portal", 1));
        result.add(new Goal(Kind.NETHER, "enter Nether", 1));
        item(result, "ancient_debris", 32);
        // The first template is loot; subsequent copies use the vanilla duplication recipe.
        item(result, "netherite_upgrade_smithing_template", 8);
        result.add(new Goal(Kind.OVERWORLD, "return home", 1));
        item(result, "raw_gold", 32); gear(result, "netherite"); item(result, "netherite_pickaxe", 1);
        item(result, "enchanting_table", 1); item(result, "bookshelf", 15); item(result, "lapis_lazuli", 24);
        result.add(new Goal(Kind.ENCHANT, "level 30 equipment enchants", 1));
        return List.copyOf(result);
    }
    private static void item(List<Goal> result, String item, int count) { result.add(new Goal(Kind.ITEM, item, count)); }
    private static void gear(List<Goal> result, String material) {
        for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots", "sword", "axe", "shovel"})
            item(result, material + "_" + piece, 1);
    }
}
