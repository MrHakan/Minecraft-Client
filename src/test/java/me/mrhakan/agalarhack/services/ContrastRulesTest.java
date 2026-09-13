package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContrastRulesTest {
    private static final int BLACK = 0x000000;
    private static final int WHITE = 0xFFFFFF;

    @Test
    void luminanceMatchesTheKnownEnds() {
        assertEquals(0.0, ContrastRules.relativeLuminance(BLACK), 1e-9);
        assertEquals(1.0, ContrastRules.relativeLuminance(WHITE), 1e-9);
    }

    @Test
    void blackOnWhiteIsTheMaximumRatio() {
        assertEquals(21.0, ContrastRules.contrastRatio(BLACK, WHITE), 0.01);
        assertEquals(21.0, ContrastRules.contrastRatio(WHITE, BLACK), 0.01,
                "the order of the arguments must not matter");
    }

    @Test
    void aColourAgainstItselfIsRatioOne() {
        assertEquals(1.0, ContrastRules.contrastRatio(0x58A6FF, 0x58A6FF), 1e-9);
    }

    @Test
    void greenWeighsMoreThanBlueWhichIsWhyTheNaiveAverageIsWrong() {
        // Pure green is far brighter to the eye than pure blue despite the same channel value.
        assertTrue(ContrastRules.relativeLuminance(0x00FF00) > ContrastRules.relativeLuminance(0x0000FF) * 5);
    }

    @Test
    void knownWcagPairsAgree() {
        // #767676 on white is the canonical "exactly passes AA" grey.
        assertEquals(4.54, ContrastRules.contrastRatio(0x767676, WHITE), 0.05);
    }

    @Test
    void readableTextIsLeftCompletelyAlone() {
        int text = 0xFFF0F5FA;
        assertEquals(text, ContrastRules.ensureReadable(text, 0x0B1017, ContrastRules.MINIMUM_RATIO));
    }

    @Test
    void unreadableTextIsBroughtUpToTheRatio() {
        // Dark grey on a nearly black panel: the case a hand-picked theme actually produces.
        int fixed = ContrastRules.ensureReadable(0xFF303030, 0x16202D, ContrastRules.MINIMUM_RATIO);
        assertTrue(ContrastRules.isReadable(fixed & 0xFFFFFF, 0x16202D, ContrastRules.MINIMUM_RATIO),
                "still " + ContrastRules.contrastRatio(fixed & 0xFFFFFF, 0x16202D));
    }

    @Test
    void alphaSurvivesTheCorrection() {
        assertEquals(0x80000000, ContrastRules.ensureReadable(0x80303030, 0x16202D, 4.5) & 0xFF000000);
        assertEquals(0xFF000000, ContrastRules.ensureReadable(0xFF303030, 0x16202D, 4.5) & 0xFF000000);
    }

    @Test
    void correctionMovesAwayFromTheBackgroundNotAlwaysToWhite() {
        int onDark = ContrastRules.ensureReadable(0xFF303030, 0x000000, 4.5) & 0xFFFFFF;
        int onLight = ContrastRules.ensureReadable(0xFFCFCFCF, 0xFFFFFF, 4.5) & 0xFFFFFF;
        assertTrue(ContrastRules.relativeLuminance(onDark) > ContrastRules.relativeLuminance(0x303030));
        assertTrue(ContrastRules.relativeLuminance(onLight) < ContrastRules.relativeLuminance(0xCFCFCF));
    }

    @Test
    void correctionKeepsSomeOfTheChosenHueWhenItCan() {
        // A slightly-too-dark blue on black should stay blue rather than being flattened to white.
        int fixed = ContrastRules.ensureReadable(0xFF1E4FA0, BLACK, ContrastRules.MINIMUM_RATIO) & 0xFFFFFF;
        int blue = fixed & 0xFF;
        int red = (fixed >> 16) & 0xFF;
        assertTrue(blue > red, "lost the hue entirely: " + Integer.toHexString(fixed));
    }

    @Test
    void anUnreachableRatioReturnsTheNearestEndRatherThanLooping() {
        // Mid-grey tops out around 5.3 against either end, so 21 is impossible.
        int fixed = ContrastRules.ensureReadable(0xFF808080, 0x808080, 21.0) & 0xFFFFFF;
        assertTrue(fixed == BLACK || fixed == WHITE, "was " + Integer.toHexString(fixed));
    }

    @Test
    void blendEndpointsAreExact() {
        assertEquals(0x102030, ContrastRules.blend(0x102030, WHITE, 0.0));
        assertEquals(WHITE, ContrastRules.blend(0x102030, WHITE, 1.0));
        assertEquals(0x102030, ContrastRules.blend(0x102030, WHITE, -5));
        assertEquals(WHITE, ContrastRules.blend(0x102030, WHITE, 5));
    }

    @Test
    void largeTextHasAGentlerRequirementThanBodyText() {
        assertTrue(ContrastRules.LARGE_TEXT_RATIO < ContrastRules.MINIMUM_RATIO);
        // #666666 on black is 3.66: too low for body text, fine for a heading.
        assertFalse(ContrastRules.isReadable(0x666666, BLACK, ContrastRules.MINIMUM_RATIO));
        assertTrue(ContrastRules.isReadable(0x666666, BLACK, ContrastRules.LARGE_TEXT_RATIO));
    }
}
