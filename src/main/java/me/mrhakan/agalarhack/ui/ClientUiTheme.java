package me.mrhakan.agalarhack.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared lightweight visual language for first-party client screens. */
public final class ClientUiTheme {
    public static int BACKGROUND = 0xF20B1017;
    public static int SIDEBAR = 0xF70E151F;
    public static int PANEL = 0xE816202D;
    public static int PANEL_HOVER = 0xEF1E2A39;
    public static int BORDER = 0xFF2B3B4E;
    public static int ACCENT = 0xFF58A6FF;
    public static int TEXT = 0xFFF0F5FA;
    public static int MUTED = 0xFF8FA3B8;
    public static int SUCCESS = 0xFF62D68B;
    public static int DANGER = 0xFFFF6B78;

    private static int radius=4;
    private static boolean shadows=true;
    public static void apply(me.mrhakan.agalarhack.services.ThemeService.Theme theme) {
        BACKGROUND=theme.background; SIDEBAR=theme.background; PANEL=((int)(theme.panelOpacity*255)<<24)|(theme.panel&0xffffff);
        PANEL_HOVER=PANEL;BORDER=theme.off;ACCENT=theme.accent;TEXT=theme.text;MUTED=theme.muted;SUCCESS=theme.on;
        radius=theme.cornerRadius;shadows=theme.shadows;
        if (theme.highContrast) {
            BACKGROUND=SIDEBAR=PANEL=PANEL_HOVER=0xff000000;
            TEXT=MUTED=BORDER=0xffffffff;
            ACCENT=0xffffff00; SUCCESS=0xff64c8ff; DANGER=0xffffa64d;
            shadows=false;
        } else {
            DANGER=0xffff6b78;
            if (theme.enforceContrast) enforceContrast();
        }
    }

    /**
     * Raises text that would be unreadable against the surface it sits on.
     *
     * <p>The high-contrast switch is all-or-nothing and nobody turns it on for this; the case that
     * actually happens is a picked or imported theme whose muted text disappears into its panel.
     * Text is measured against the panel, since that is what it is drawn on almost everywhere.
     *
     * <p>The accent is held to the gentler large-text ratio because it is used for headings, stripes
     * and borders rather than body copy, and forcing it to 4.5 would flatten every theme's identity.
     */
    private static void enforceContrast() {
        int surface = 0xFF000000 | (PANEL & 0x00FFFFFF);
        TEXT = me.mrhakan.agalarhack.services.ContrastRules.ensureReadable(TEXT, surface,
                me.mrhakan.agalarhack.services.ContrastRules.MINIMUM_RATIO);
        MUTED = me.mrhakan.agalarhack.services.ContrastRules.ensureReadable(MUTED, surface,
                me.mrhakan.agalarhack.services.ContrastRules.MINIMUM_RATIO);
        ACCENT = me.mrhakan.agalarhack.services.ContrastRules.ensureReadable(ACCENT, surface,
                me.mrhakan.agalarhack.services.ContrastRules.LARGE_TEXT_RATIO);
        SUCCESS = me.mrhakan.agalarhack.services.ContrastRules.ensureReadable(SUCCESS, surface,
                me.mrhakan.agalarhack.services.ContrastRules.LARGE_TEXT_RATIO);
        DANGER = me.mrhakan.agalarhack.services.ContrastRules.ensureReadable(DANGER, surface,
                me.mrhakan.agalarhack.services.ContrastRules.LARGE_TEXT_RATIO);
    }
    private ClientUiTheme() {
    }

    public static void backdrop(GuiGraphicsExtractor g, int width, int height) {
        g.fill(0, 0, width, height, BACKGROUND);
        g.fill(0, 0, width, 2, ACCENT);
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean highlighted) {
        int r=Math.min(radius,Math.max(0,Math.min(width,height)/2));
        if(shadows)g.fill(x+2,y+3,x+width+2,y+height+3,0x30000000);
        g.fill(x+r,y,x+width-r,y+height,highlighted?PANEL_HOVER:PANEL);
        g.fill(x,y+r,x+width,y+height-r,highlighted?PANEL_HOVER:PANEL);
        for(int row=0;row<r;row++){
            int inset=(int)Math.ceil(r-Math.sqrt(Math.max(0,r*r-(r-row)*(r-row))));
            g.fill(x+inset,y+row,x+width-inset,y+row+1,PANEL);
            g.fill(x+inset,y+height-row-1,x+width-inset,y+height-row,PANEL);
        }
        g.fill(x+r,y,x+width-r,y+1,highlighted?ACCENT:BORDER);
    }
}
