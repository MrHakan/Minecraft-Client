package me.mrhakan.agalarhack.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared lightweight visual language for first-party client screens. */
public final class ClientUiTheme {
    public static final int BACKGROUND = 0xF20B1017;
    public static final int SIDEBAR = 0xF70E151F;
    public static final int PANEL = 0xE816202D;
    public static final int PANEL_HOVER = 0xEF1E2A39;
    public static final int BORDER = 0xFF2B3B4E;
    public static final int ACCENT = 0xFF58A6FF;
    public static final int TEXT = 0xFFF0F5FA;
    public static final int MUTED = 0xFF8FA3B8;
    public static final int SUCCESS = 0xFF62D68B;
    public static final int DANGER = 0xFFFF6B78;

    private ClientUiTheme() {
    }

    public static void backdrop(GuiGraphicsExtractor g, int width, int height) {
        g.fill(0, 0, width, height, BACKGROUND);
        g.fill(0, 0, width, 2, ACCENT);
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean highlighted) {
        g.fill(x, y, x + width, y + height, highlighted ? PANEL_HOVER : PANEL);
        g.outline(x, y, width, height, highlighted ? ACCENT : BORDER);
    }
}
