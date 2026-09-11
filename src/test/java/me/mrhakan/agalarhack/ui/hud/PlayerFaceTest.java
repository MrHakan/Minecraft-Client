package me.mrhakan.agalarhack.ui.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayerFaceTest {

    private static boolean inside(int u, int v) {
        return u >= 0 && v >= 0
                && u + PlayerFace.PATCH <= PlayerFace.SKIN_WIDTH
                && v + PlayerFace.PATCH <= PlayerFace.SKIN_HEIGHT;
    }

    @Test
    void bothPatchesLieInsideTheSkinTexture() {
        assertTrue(inside(PlayerFace.FACE_U, PlayerFace.FACE_V), "face patch is off the texture");
        assertTrue(inside(PlayerFace.HAT_U, PlayerFace.HAT_V), "hat patch is off the texture");
    }

    @Test
    void theHatIsADifferentPatchFromTheFace() {
        // Pointing both at the same place would draw the face twice and lose every hood and fringe.
        assertTrue(PlayerFace.FACE_U != PlayerFace.HAT_U || PlayerFace.FACE_V != PlayerFace.HAT_V);
    }

    @Test
    void theTwoPatchesDoNotOverlap() {
        boolean separateColumns = Math.abs(PlayerFace.FACE_U - PlayerFace.HAT_U) >= PlayerFace.PATCH;
        boolean separateRows = Math.abs(PlayerFace.FACE_V - PlayerFace.HAT_V) >= PlayerFace.PATCH;
        assertTrue(separateColumns || separateRows);
    }

    @Test
    void noFaceMeansNoLayoutChangeAtAll() {
        assertEquals(0, PlayerFace.textIndent(false), "an unchanged card must be identical, not merely similar");
        assertEquals(0, PlayerFace.minimumContentHeight(false));
    }

    @Test
    void theIndentClearsTheFaceWithTheGutter() {
        assertEquals(PlayerFace.DRAWN_SIZE + PlayerFace.GUTTER, PlayerFace.textIndent(true));
        assertTrue(PlayerFace.textIndent(true) > PlayerFace.DRAWN_SIZE, "text would sit on top of the face");
    }

    @Test
    void theCardStillFitsAFaceTallerThanItsText() {
        assertEquals(PlayerFace.DRAWN_SIZE, PlayerFace.minimumContentHeight(true));
    }

    @Test
    void theFaceIsScaledUpRatherThanDrawnAtSourceSize() {
        assertTrue(PlayerFace.DRAWN_SIZE > PlayerFace.PATCH, "an 8px face is unreadable on a HUD card");
        assertEquals(0, PlayerFace.DRAWN_SIZE % PlayerFace.PATCH, "a fractional scale makes skin pixels uneven");
    }
}
