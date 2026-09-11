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
        int index = 0;
        for (var row : rows) {
            int color = theme.highContrast ? ClientUiTheme.ACCENT : switch (options.getStringSetting("colorMode", "Rainbow")) {
                case "Accent" -> ClientUiTheme.ACCENT;
                case "Category" -> categoryColor(row.category());
                default -> Hud.rainbow(++index*300);
            };
            String text = mc.font.plainSubstrByWidth(row.text(), Math.max(0, width-padding*2));
            if (background) g.fill(x,y,x+width,y+rowHeight,ClientUiTheme.PANEL);
            if (sideBar) { int edge=right?x+width-2:x; g.fill(edge,y,edge+2,y+rowHeight,color); }
            int textX=right?x+width-padding-mc.font.width(text):x+padding;
            g.text(mc.font,text,textX,y+(background?2:0),color,shadow);
            y+=rowHeight;
        }
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
