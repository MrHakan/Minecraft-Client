package me.mrhakan.agalarhack.services;

/**
 * Minecraft 26.2's own critical-hit rule, restated so a module can wait for the moment.
 *
 * <p>This is <em>not</em> a guess or a carried-over rule from an older version: it mirrors
 * {@code Player.canCriticalAttack} in 26.2 exactly, read from the deobfuscated jar. That matters
 * because the rule has changed — 26.2 has no blindness term, which older clients' crit checks
 * include, and {@code fallDistance} is a {@code double} here rather than a {@code float}. A module
 * waiting on the wrong condition would hold its attack for a crit that was never coming.
 *
 * <p>The target-is-living term of the real rule is not represented, because every caller here only
 * ever attacks living entities; a flag that is always true would just be noise.
 *
 * <p>Pure, so the rule can be compared against the game's own without a client.
 */
public final class CriticalHits {
    private CriticalHits() { }

    /** The player state the rule reads, in the order {@code Player.canCriticalAttack} tests it. */
    public record State(double fallDistance, boolean onGround, boolean onClimbable, boolean inWater,
                        boolean mobilityRestricted, boolean passenger, boolean sprinting) { }

    /** @return true when an attack landed now would be a critical hit */
    public static boolean wouldCrit(State state) {
        return state != null
                && state.fallDistance() > 0
                && !state.onGround()
                && !state.onClimbable()
                && !state.inWater()
                && !state.mobilityRestricted()
                && !state.passenger()
                && !state.sprinting();
    }
}
