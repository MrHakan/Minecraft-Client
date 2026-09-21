package me.mrhakan.agalarhack.ui.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.gui.components.PlayerFaceExtractor;
import org.junit.jupiter.api.Test;

class PlayerFaceTest {

    private static boolean inside(int u, int v) {
        return u >= 0 && v >= 0
                && u + PlayerFace.PATCH <= PlayerFace.SKIN_WIDTH
                && v + PlayerFace.PATCH <= PlayerFace.SKIN_HEIGHT;
    }

    /**
     * The skin layout is Minecraft's, not ours, so it is checked against Minecraft's own numbers.
     *
     * <p>Until now these constants asserted only that they were self-consistent: inside the texture,
     * not overlapping, not identical. All of that would still hold if every one of them were wrong.
     * 26.2 publishes the layout on {@code PlayerFaceExtractor}, so the copy in {@link PlayerFace} is
     * now tied to it and a version that moved the face or the hat fails the build instead of drawing
     * the wrong eight pixels.
     *
     * <p>These are compile-time constants on both sides, so javac inlines them and no client class
     * is loaded to run this. That is what makes it safe in a unit test, and it does not weaken the
     * check: both values come from the jar being compiled against, so a divergence fails the next
     * build rather than going unnoticed.
     */
    @Test
    void theLayoutMatchesMinecraftsOwnSkinConstants() {
        assertEquals(PlayerFaceExtractor.SKIN_HEAD_U, PlayerFace.FACE_U, "face U");
        assertEquals(PlayerFaceExtractor.SKIN_HEAD_V, PlayerFace.FACE_V, "face V");
        assertEquals(PlayerFaceExtractor.SKIN_HAT_U, PlayerFace.HAT_U, "hat U");
        assertEquals(PlayerFaceExtractor.SKIN_HAT_V, PlayerFace.HAT_V, "hat V");
        assertEquals(PlayerFaceExtractor.SKIN_HEAD_WIDTH, PlayerFace.PATCH, "patch width");
        assertEquals(PlayerFaceExtractor.SKIN_HEAD_HEIGHT, PlayerFace.PATCH, "patch height");
        assertEquals(PlayerFaceExtractor.SKIN_HAT_WIDTH, PlayerFace.PATCH, "hat patch width");
        assertEquals(PlayerFaceExtractor.SKIN_HAT_HEIGHT, PlayerFace.PATCH, "hat patch height");
        assertEquals(PlayerFaceExtractor.SKIN_TEX_WIDTH, PlayerFace.SKIN_WIDTH, "texture width");
        assertEquals(PlayerFaceExtractor.SKIN_TEX_HEIGHT, PlayerFace.SKIN_HEIGHT, "texture height");
    }

    /**
     * The face is drawn at exactly twice its source size.
     *
     * <p>26.2's blit takes the drawn size and the source size as separate pairs -
     * {@code blit(pipeline, texture, x, y, u, v, width, height, srcWidth, srcHeight, texWidth,
     * texHeight, colour)} - and every one of those is an {@code int}, so swapping a pair compiles
     * perfectly and stretches or crops the face. This pins the relationship the renderer relies on.
     */
    @Test
    void theFaceIsDrawnAtAWholeMultipleOfTheSourcePatch() {
        assertEquals(0, PlayerFace.DRAWN_SIZE % PlayerFace.PATCH,
                "a drawn size that is not a whole multiple of the patch samples between texels");
        assertEquals(2, PlayerFace.DRAWN_SIZE / PlayerFace.PATCH);
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
