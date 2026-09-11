package me.mrhakan.agalarhack.ui.hud;

/**
 * Where the face sits on a player skin, and how much room drawing it needs.
 *
 * <p>A skin is a fixed 64x64 layout: the head front is an 8x8 patch, and the hat layer is a second
 * 8x8 patch drawn over it. Both are needed — skipping the hat leaves a bald head for anyone whose
 * skin puts hair, a hood or glasses on that layer, which is most of them.
 *
 * <p>The numbers live here rather than inline in the renderer so the one thing that can silently go
 * wrong — a patch reaching outside the texture, or the two patches pointing at the same place — is
 * something a test can catch without a running client.
 */
public final class PlayerFace {
    private PlayerFace() { }

    public static final int SKIN_WIDTH = 64;
    public static final int SKIN_HEIGHT = 64;
    public static final int PATCH = 8;

    /** Head front. */
    public static final int FACE_U = 8;
    public static final int FACE_V = 8;

    /** Hat layer, drawn over the face. */
    public static final int HAT_U = 40;
    public static final int HAT_V = 8;

    /** Drawn at twice the source size, which reads next to three lines of text without dominating. */
    public static final int DRAWN_SIZE = 16;

    /** Gap between the face and the text beside it. */
    public static final int GUTTER = 4;

    /**
     * How far the card's text has to move right to clear the face.
     *
     * @return 0 when no face is drawn, so an existing layout is unchanged rather than merely similar
     */
    public static int textIndent(boolean showFace) {
        return showFace ? DRAWN_SIZE + GUTTER : 0;
    }

    /** The card's height must still fit the face even when the text is shorter than it. */
    public static int minimumContentHeight(boolean showFace) {
        return showFace ? DRAWN_SIZE : 0;
    }
}
