package me.mrhakan.agalarhack.services;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Registry id strings, built once per entry rather than once per lookup.
 *
 * <p>BlockESP classifies every block it probes by id, StorageESP every block entity it meets, and
 * both overlays classify every marker again on every frame; ItemESP and InventoryCleaner match item
 * ids against their lists every tick. {@code Identifier.toString()} builds a fresh string each time,
 * so those paths allocated one string per probed block, per marker per frame and per item per tick.
 * A built-in registry is frozen before the client reaches a world, so an entry's id never changes
 * and the string is safe to keep for the whole session.
 *
 * <p>The source function is injected so the caching itself is tested without a bootstrapped registry.
 */
public final class StableIds<K> {
    /** Block ids such as {@code minecraft:chest}. */
    public static final StableIds<Block> BLOCKS =
            new StableIds<>(block -> BuiltInRegistries.BLOCK.getKey(block).toString());
    /** Item ids such as {@code minecraft:diamond}. */
    public static final StableIds<Item> ITEMS =
            new StableIds<>(item -> BuiltInRegistries.ITEM.getKey(item).toString());

    private final Map<K, String> ids = new ConcurrentHashMap<>();
    private final Function<K, String> source;

    public StableIds(Function<K, String> source) {
        this.source = Objects.requireNonNull(source);
    }

    /** @param entry a registry entry; never null */
    public String of(K entry) {
        String id = ids.get(entry);
        return id != null ? id : ids.computeIfAbsent(entry, source);
    }

    public static String block(Block block) {
        return BLOCKS.of(block);
    }

    public static String item(Item item) {
        return ITEMS.of(item);
    }
}
