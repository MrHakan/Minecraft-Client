package me.mrhakan.agalarhack.services.scanning;

/**
 * Classifies a standing position as a hole and how much protection it offers.
 *
 * <p>A hole is somewhere a player fits with solid blocks on all four sides and below. What makes one
 * worth marking is the surrounding material: obsidian and bedrock survive a crystal, ordinary blocks
 * do not, and calling a stone pocket "safe" would be actively dangerous advice.
 *
 * <p>Free of Minecraft types so the rules are unit tested; the module maps real block states onto
 * {@link Material} and passes the result in.
 */
public final class HoleDetector {
    private HoleDetector() { }

    /** How a neighbouring block behaves under an explosion, from the client's point of view. */
    public enum Material {
        /** Air, or anything a player is not blocked by. */
        OPEN,
        /** Solid but destructible by a crystal. */
        WEAK,
        /** Obsidian, bedrock and similar blast-resistant blocks. */
        RESISTANT
    }

    /** What a candidate position turned out to be. */
    public enum Hole {
        /** Not a hole: something is open, or there is no room to stand. */
        NONE,
        /** Enclosed, but at least one side would break. */
        UNSAFE,
        /** Enclosed by blast-resistant blocks on every side and below. */
        SAFE
    }

    /**
     * @param floor        block under the player's feet
     * @param sides        the four horizontal neighbours at foot level
     * @param feetClear    the player's feet position is open
     * @param headClear    the position above the feet is open
     * @return the classification; {@link Hole#NONE} when it is not a standable hole
     */
    public static Hole classify(Material floor, Material[] sides, boolean feetClear, boolean headClear) {
        if (floor == null || sides == null || sides.length != 4) return Hole.NONE;
        // A hole the player cannot stand in is not a hole worth marking.
        if (!feetClear || !headClear) return Hole.NONE;
        if (floor == Material.OPEN) return Hole.NONE;

        boolean allResistant = floor == Material.RESISTANT;
        for (Material side : sides) {
            if (side == null || side == Material.OPEN) return Hole.NONE;
            if (side != Material.RESISTANT) allResistant = false;
        }
        return allResistant ? Hole.SAFE : Hole.UNSAFE;
    }
}
