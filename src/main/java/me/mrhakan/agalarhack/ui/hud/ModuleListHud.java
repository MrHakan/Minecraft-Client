package me.mrhakan.agalarhack.ui.hud;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.managers.HudLayoutManager;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.ThemeService;
import me.mrhakan.agalarhack.ui.ClientUiTheme;
import me.mrhakan.agalarhack.ui.Hud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Uses exactly the same measured rectangle for drawing and HUD editor anchors. */
public final class ModuleListHud {
    private int width = 120, height = 12;
    /**
     * Per-row slide and fade. Deliberately does not affect {@link #width()} or {@link #height()}:
     * the HUD editor anchors to the measured rectangle, and a box that changed size mid-animation
     * would make the anchor jitter. Only the rows move inside it.
     */
    private final RowAnimations animations = new RowAnimations();
    public int width() { return width; }
    public int height() { return height; }

    public void render(GuiGraphicsExtractor g, Minecraft mc) {
        var manager = AgalarHackClient.moduleManager;
        var options = manager.getModule("ModuleList");
        width = 120; height = mc.font.lineHeight;
        if (options == null || !options.isToggled()) return;
        boolean background = options.getBooleanSetting("background", false);
        boolean sideBar = options.getBooleanSetting("sideBar", false);
        int padding = background || sideBar ? 4 : 0, rowHeight = mc.font.lineHeight + (background ? 4 : 0);
        int max = Math.min((int) options.getNumberSetting("maximumRows", 32), Math.max(0, (g.guiHeight()-8)/rowHeight));
        var entries = manager.getModuleList().stream().filter(module -> module.isToggled() && module.getBooleanSetting("showInHud", true))
                .map(module -> new ModuleListModel.Entry(module.getName(), module.getDisplayName(), module.getCategory().name)).toList();
        var rows = ModuleListModel.rows(entries, options.getStringSetting("displayMode", "Display"),
                options.getStringSetting("letterCase", "Normal"), options.getStringSetting("sorting", "Width"), max, mc.font::width);
        if (rows.isEmpty()) return;
        width = Math.max(1, Math.min(Math.max(1,g.guiWidth()-8), rows.stream().mapToInt(ModuleListModel.Row::width).max().orElse(0)+padding*2));
        height = rows.size()*rowHeight;
        var layout = AgalarHackClient.HUD_LAYOUT;
        int x = layout.resolveX("modules", g.guiWidth(), width), y = layout.resolveY("modules", g.guiHeight(), height);
        String alignment = options.getStringSetting("alignment", "Right");
        var anchor = layout.get("modules").anchor;
        boolean right = alignment.equals("Right") || alignment.equals("Anchor") &&
                (anchor == HudLayoutManager.Anchor.TOP_RIGHT || anchor == HudLayoutManager.Anchor.BOTTOM_RIGHT);
        boolean shadow = options.getBooleanSetting("textShadow", true);
        var theme = ClientServices.require(ThemeService.class).current();
        // Reduced motion and the theme's animation switch both override the module's own toggle,
        // the same way the target HUD's health bar treats them.
        boolean animate = options.getBooleanSetting("rowAnimations", true) && theme.motionEnabled();
        // Rows slide in from the aligned edge, so the motion runs outward rather than across the text.
        double slide = (right ? 1 : -1) * options.getNumberSetting("slideDistance", 14);
        var placed = animations.update(rows.stream().map(ModuleListModel.Row::text).toList(),
                rowHeight, slide, options.getNumberSetting("animationSpeed", 0.25), animate);

        int index = 0;
        for (var row : rows) {
            int color = theme.highContrast ? ClientUiTheme.ACCENT : switch (options.getStringSetting("colorMode", "Rainbow")) {
                case "Accent" -> ClientUiTheme.ACCENT;
                case "Category" -> categoryColor(row.category());
                default -> Hud.rainbow(++index*300);
            };
            var motion = placed.stream().filter(candidate -> candidate.id().equals(row.text())).findFirst().orElse(null);
            int rowX = x + (motion == null ? 0 : (int) Math.round(motion.offsetX()));
            int rowY = y + (motion == null ? 0 : (int) Math.round(motion.y()));
            int alpha = motion == null ? 255 : (int) Math.round(Math.max(0, Math.min(1, motion.alpha())) * 255);
            if (alpha <= 0) continue;
            String text = mc.font.plainSubstrByWidth(row.text(), Math.max(0, width-padding*2));
            if (background) g.fill(rowX,rowY,rowX+width,rowY+rowHeight,fade(ClientUiTheme.PANEL, alpha));
            if (sideBar) { int edge=right?rowX+width-2:rowX; g.fill(edge,rowY,edge+2,rowY+rowHeight,fade(color, alpha)); }
            int textX=right?rowX+width-padding-mc.font.width(text):rowX+padding;
            g.text(mc.font,text,textX,rowY+(background?2:0),fade(color, alpha),shadow);
        }
    }

    /** Scales a colour's existing alpha, so a translucent panel stays translucent while it fades. */
    private static int fade(int argb, int alpha) {
        int existing = (argb >>> 24) == 0 ? 255 : argb >>> 24;
        return ((existing * Math.max(0, Math.min(255, alpha)) / 255) << 24) | (argb & 0x00FFFFFF);
    }

    private static int categoryColor(String category) {
        return switch (category) {
            case "Combat" -> 0xffff8989;
            case "Movement" -> 0xff80c8ff;
            case "Render" -> 0xffc8a0ff;
            case "World" -> 0xff83dab6;
            case "Misc" -> 0xffffd487;
            default -> ClientUiTheme.ACCENT;
        };
    }
}
