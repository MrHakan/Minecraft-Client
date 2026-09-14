package me.mrhakan.agalarhack.services;

import net.minecraft.world.level.block.state.BlockState;

/**
 * The one place this client asks whether a block stops a body from moving through it.
 *
 * <p>Three modules need that question answered - SpawnESP for whether a mob could stand somewhere,
 * HoleESP for whether a space is open, Parkour for whether there is ground beyond the edge - and all
 * three had their own call to the same method.
 *
 * <p>That method is {@code BlockState.blocksMotion()}, which 26.2 deprecates, and the deprecation is
 * the reason this class exists rather than being three copies. <strong>It is deliberately not
 * migrated.</strong> There is no replacement with the same meaning: {@code isCollisionShapeFullBlock}
 * is false for a slab or a fence that plainly does stop a body, an empty collision shape misses the
 * cobweb and bamboo-sapling exclusions this method makes by name, and Minecraft itself still calls it
 * internally - the default {@code isSuffocating} predicate is {@code blocksMotion() &&
 * isCollisionShapeFullBlock(...)}. Swapping in something with different semantics to silence a
 * compiler note would change what these three modules draw and where Parkour jumps.
 *
 * <p>So the deprecation is acknowledged in one place, with the reasoning next to it, instead of
 * five warnings across three files and no explanation anywhere. If a 26.2+ release ever adds a
 * like-for-like replacement, this is the only line to change.
 */
public final class BlockPresence {
    private BlockPresence() { }

    /**
     * @return true when this state would stop a body moving into it
     */
    @SuppressWarnings("deprecation")
    public static boolean blocksMotion(BlockState state) {
        return state != null && state.blocksMotion();
    }

    /** The opposite, for the readers that want it that way round: air, or anything passable. */
    public static boolean isPassable(BlockState state) {
        return !blocksMotion(state);
    }
}
