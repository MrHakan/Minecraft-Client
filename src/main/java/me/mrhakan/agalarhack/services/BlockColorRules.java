package me.mrhakan.agalarhack.services;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves the overlay colour for a single scanned block.
 *
 * <p>BlockESP originally painted every match with one colour, which stops being useful as soon as the
 * filter matches more than one kind of block: a wall of identical boxes tells the player where
 * something is but not what it is. Colour is the cheapest way to carry that, so this decides it in
 * three tiers — an explicit per-id override the player typed, then a built-in colour for blocks worth
 * distinguishing, then the module's own colour sliders as the fallback.
 *
 * <p>Kept free of Minecraft types so the parsing and precedence rules are unit tested directly.
 */
public final class BlockColorRules {
    private BlockColorRules() { }

    public static final int MAX_ENTRIES = 64;

    /** Returned by {@link #categoryColor} when there is no built-in opinion about an id. */
    public static final int NO_COLOR = -1;

    /**
     * Parses {@code id=RRGGBB} pairs into canonical ids.
     *
     * <p>Entries are separated by commas, semicolons or newlines. The id goes through
     * {@link ItemIdList#canonical} so {@code diamond_ore} and {@code minecraft:diamond_ore} are the
     * same key. The colour accepts an optional {@code #} and must be exactly six hex digits — three
     * digit shorthand is deliberately not accepted, because {@code f00} is far more likely to be a
     * truncated paste than an intentional shorthand, and silently reading it as red would hide the
     * mistake.
     *
     * <p>A malformed entry is dropped rather than guessed at, and a later entry for the same id wins,
     * so editing the setting behaves the way the last-write-wins text implies.
     *
     * @return an ordered map of canonical id to {@code 0xRRGGBB}; never null
     */
    public static Map<String, Integer> parse(String raw) {
        Map<String, Integer> colors = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return colors;
        for (String part : raw.split("[,\\n;]")) {
            int equals = part.indexOf('=');
            if (equals < 0) continue;
            String id = ItemIdList.canonical(part.substring(0, equals));
            if (id == null) continue;
            int rgb = parseColor(part.substring(equals + 1));
            if (rgb == NO_COLOR) continue;
            // Re-inserting an existing key keeps its original position, which is what the player sees
            // in the text they typed, so remove first to make the last entry genuinely last.
            colors.remove(id);
            colors.put(id, rgb);
            if (colors.size() >= MAX_ENTRIES) break;
        }
        return colors;
    }

    /** @return {@code 0xRRGGBB}, or {@link #NO_COLOR} when the text is not exactly six hex digits */
    public static int parseColor(String raw) {
        if (raw == null) return NO_COLOR;
        String trimmed = raw.trim();
        if (trimmed.startsWith("#")) trimmed = trimmed.substring(1);
        if (trimmed.length() != 6) return NO_COLOR;
        int value = 0;
        for (int index = 0; index < 6; index++) {
            int digit = Character.digit(trimmed.charAt(index), 16);
            if (digit < 0) return NO_COLOR;
            value = (value << 4) | digit;
        }
        return value;
    }

    /**
     * Built-in colours for blocks the player actually distinguishes at a glance.
     *
     * <p>These follow the block's own appearance rather than a rarity scale, because that is what
     * makes a marker recognisable without a legend. Namespace is ignored: a modded
     * {@code someothermod:diamond_ore} is still a diamond ore to the person looking at it.
     *
     * @return {@code 0xRRGGBB}, or {@link #NO_COLOR} when nothing specific is known
     */
    public static int categoryColor(String id) {
        if (id == null) return NO_COLOR;
        String path = id.toLowerCase(Locale.ROOT);
        int separator = path.indexOf(':');
        if (separator >= 0) path = path.substring(separator + 1);
        // Deepslate variants share their ore's colour: the player is looking for the ore, not the stone.
        if (path.startsWith("deepslate_")) path = path.substring("deepslate_".length());
        return switch (path) {
            case "diamond_ore" -> 0x4AEDD9;
            case "emerald_ore" -> 0x41F384;
            case "ancient_debris" -> 0x8A5C4B;
            case "gold_ore", "nether_gold_ore" -> 0xFCD34D;
            case "iron_ore" -> 0xD8AF93;
            case "copper_ore" -> 0xE07B53;
            case "coal_ore" -> 0x4A4A4A;
            case "redstone_ore" -> 0xE03030;
            case "lapis_ore" -> 0x2E5AD1;
            case "nether_quartz_ore" -> 0xEDE0D4;
            case "spawner" -> 0xB44AED;
            case "trial_spawner", "vault", "ominous_vault" -> 0xED9A4A;
            case "nether_portal", "end_portal", "end_gateway", "end_portal_frame" -> 0xA855F7;
            case "beacon", "conduit" -> 0x5EEAD4;
            case "chest", "trapped_chest", "barrel", "shulker_box" -> 0xC08A3E;
            case "ender_chest" -> 0x3D7A6E;
            default -> NO_COLOR;
        };
    }

    /**
     * Applies the three tiers in order.
     *
     * @param overrides parsed {@link #parse} result; may be empty but not null
     * @param id canonical block id
     * @param useCategories whether the built-in colours participate at all
     * @param fallback the module's own colour, used when nothing more specific applies
     */
    public static int resolve(Map<String, Integer> overrides, String id, boolean useCategories, int fallback) {
        Integer override = overrides.get(id);
        if (override != null) return override;
        if (useCategories) {
            int category = categoryColor(id);
            if (category != NO_COLOR) return category;
        }
        return fallback;
    }
}
