package me.mrhakan.agalarhack.module.movement;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The geometry of Parkour's support probe, which decides how late the module jumps.
 *
 * <p>Pure arithmetic on purpose. The game test proves the probe finds real support on a fence and a
 * wall; what it cannot show is the probe reaching <em>backwards</em> over the ground the player is
 * standing on, because a jump that comes 0.3 blocks late is still a jump and the scenario only
 * measures that the player rose.
 */
class ParkourProbeTest {
    /** Vanilla player width; the box a swept-footprint probe would have used. */
    private static final double PLAYER_HALF_WIDTH = 0.3;

    /** The first solid x the probe may not touch, for a player at the origin travelling +x. */
    private static double trailingEdge(double lookahead) {
        return Parkour.supportProbe(lookahead, 64.0, 0.0).minX;
    }

    @Test void theProbeIsAColumnAtTheLookAheadPointNotTheSweptPlayerBox() {
        AABB probe = Parkour.supportProbe(0.35, 64.0, 0.0);
        assertEquals(0.35, (probe.minX + probe.maxX) / 2, 1.0E-9, "centred on the look-ahead point");
        assertEquals(0.0, (probe.minZ + probe.maxZ) / 2, 1.0E-9);
        assertTrue(probe.maxX - probe.minX < 2 * PLAYER_HALF_WIDTH,
                "a probe as wide as the player reaches back over its own footing");
        assertEquals(63.8, probe.minY, 1.0E-9, "the 0.2 drop depth the block-state probe used");
        assertEquals(64.0, probe.maxY, 1.0E-9, "up to the feet, not past them");
    }

    /**
     * The regression this pins: sweeping the player's own box forward costs the look-ahead setting
     * the player's half-width, so "0.35 blocks ahead" became 0.05 and the slider's lower half never
     * fired in time at all.
     */
    @Test void everyLookAheadTheSliderAllowsStillClearsThePlayersOwnFooting() {
        for (double lookahead : new double[] {0.1, 0.2, 0.35, 0.5, 1.0}) {
            double sweptWouldReachBackTo = lookahead - PLAYER_HALF_WIDTH;
            assertTrue(trailingEdge(lookahead) > sweptWouldReachBackTo,
                    "look-ahead " + lookahead + " reaches back to " + trailingEdge(lookahead));
            assertTrue(trailingEdge(lookahead) > 0,
                    "look-ahead " + lookahead + " probes the player's own position, so a ledge "
                            + "cannot be seen until the player is already over it");
        }
    }

    /** The trigger distance the setting promises, to within the probe's own half-width. */
    @Test void theLedgeIsFoundWithinTheLookAheadItPromises() {
        for (double lookahead : new double[] {0.1, 0.35, 1.0}) {
            double lost = lookahead - trailingEdge(lookahead);
            assertTrue(lost <= 0.05 + 1.0E-9,
                    "look-ahead " + lookahead + " loses " + lost + " blocks of reach");
        }
    }
}
