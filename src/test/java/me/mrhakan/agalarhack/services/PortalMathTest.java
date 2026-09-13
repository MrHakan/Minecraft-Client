package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PortalMathTest {

    @Test
    void overworldToNetherDividesByEight() {
        assertEquals(new PortalMath.Link(PortalMath.NETHER, 100, -50),
                PortalMath.pair(PortalMath.OVERWORLD, 800, -400));
    }

    @Test
    void netherToOverworldMultipliesByEight() {
        assertEquals(new PortalMath.Link(PortalMath.OVERWORLD, 800, -400),
                PortalMath.pair(PortalMath.NETHER, 100, -50));
    }

    /**
     * The pairing floors, because that is what the game does - checked against 26.2 rather than
     * assumed.
     *
     * <p>`NetherPortalBlock` multiplies the entity's position by the coordinate scale as a double
     * and hands it to `WorldBorder.clampToBounds(double, double, double)`, which is
     * `BlockPos.containing`, which is `Mth.floor`. Integer division truncates toward zero instead,
     * so every negative coordinate not already on a multiple of eight came out one block too high:
     * -7 paired to 0 rather than -1. This assertion used to require that, with a comment claiming
     * integer division was what the game did.
     */
    @Test
    void divisionFloorsOnBothSidesOfTheAxis() {
        assertEquals(0, PortalMath.divide(7));
        assertEquals(-1, PortalMath.divide(-7));
        assertEquals(1, PortalMath.divide(8));
        assertEquals(-1, PortalMath.divide(-8));
        assertEquals(-2, PortalMath.divide(-9));
        assertEquals(0, PortalMath.divide(0));
        assertEquals(-1, PortalMath.divide(-1));
    }

    @Test
    void aRoundTripThroughTheNetherLandsInsideOneNetherBlock() {
        for (int x : new int[] { 0, 1, 7, 8, 100, 12_345, -1, -7, -8, -12_345 }) {
            var nether = PortalMath.pair(PortalMath.OVERWORLD, x, 0);
            var back = PortalMath.pair(PortalMath.NETHER, nether.x(), nether.z());
            assertTrue(Math.abs(back.x() - x) < PortalMath.NETHER_RATIO,
                    x + " came back as " + back.x());
        }
    }

    @Test
    void theEndDoesNotPairByCoordinate() {
        assertNull(PortalMath.pair(PortalMath.END, 100, 100),
                "saying 1:1 would suggest a link that does not exist");
    }

    @Test
    void anUnknownDimensionSaysNothingRatherThanGuessing() {
        assertNull(PortalMath.pair("somemod:gardens", 100, 100));
        assertNull(PortalMath.pair(null, 100, 100));
    }

    @Test
    void dimensionIdsAreCaseInsensitive() {
        assertEquals(PortalMath.pair(PortalMath.OVERWORLD, 800, 800),
                PortalMath.pair("Minecraft:Overworld", 800, 800));
    }

    @Test
    void multiplyingOutCannotOverflowPastTheWorldBorder() {
        var link = PortalMath.pair(PortalMath.NETHER, Integer.MAX_VALUE, Integer.MIN_VALUE);
        assertEquals(PortalMath.MAX_HORIZONTAL, link.x());
        assertEquals(-PortalMath.MAX_HORIZONTAL, link.z());
    }

    @Test
    void theSearchRadiusDiffersByDirection() {
        assertTrue(PortalMath.searchRadius(PortalMath.NETHER) < PortalMath.searchRadius(PortalMath.OVERWORLD),
                "the nether side searches a tighter radius, which is why nether-side building is precise");
    }

    @Test
    void shortNamesCoverTheThreeVanillaDimensionsAndKeepAnythingElse() {
        assertEquals("overworld", PortalMath.shortName(PortalMath.OVERWORLD));
        assertEquals("nether", PortalMath.shortName(PortalMath.NETHER));
        assertEquals("end", PortalMath.shortName(PortalMath.END));
        assertEquals("somemod:gardens", PortalMath.shortName("somemod:gardens"));
        assertEquals("unknown", PortalMath.shortName(null));
    }
}
