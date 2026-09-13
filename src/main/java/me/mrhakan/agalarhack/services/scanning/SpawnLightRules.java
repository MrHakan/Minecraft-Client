package me.mrhakan.agalarhack.services.scanning;

/**
 * Classifies a position by whether hostile mobs could spawn there, using light levels only.
 *
 * <p>Scope is deliberately narrow and must stay that way. Since 1.18 hostile mobs require a block
 * light level of zero; sky light then decides whether the spot is dangerous only at night or at any
 * time. What this does <em>not</em> model is biome rules, mob-specific placement, spawn caps,
 * difficulty, or the many blocks that forbid spawning on their own. A position marked here is
 * "light permits it", not "a mob will appear", and every caller must present it that way.
 *
 * <p>Free of Minecraft types so the thresholds are unit tested.
 */
public final class SpawnLightRules {
    private SpawnLightRules() { }

    public enum Spawnable {
        /** Block light is high enough to prevent spawning from light alone. */
        NONE,
        /** Light permits spawning once the sky is dark. */
        NIGHT,
        /** Light permits spawning regardless of the time of day. */
        ALWAYS
    }

    /**
     * @param blockLight 0..15 block light at the position
     * @param skyLight   0..15 sky light at the position
     * @param standable  the position is open with headroom above a solid floor
     */
    public static Spawnable classify(int blockLight, int skyLight, boolean standable) {
        if (!standable) return Spawnable.NONE;
        if (blockLight < 0 || blockLight > 15 || skyLight < 0 || skyLight > 15) return Spawnable.NONE;
        // Block light is the only condition the client can check that holds at every time of day.
        if (blockLight > 0) return Spawnable.NONE;
        // With no block light, the sky is what is left: fully enclosed means always spawnable, and
        // anything exposed to the sky becomes spawnable once the sky darkens, whatever its daytime level.
        return skyLight == 0 ? Spawnable.ALWAYS : Spawnable.NIGHT;
    }
}
