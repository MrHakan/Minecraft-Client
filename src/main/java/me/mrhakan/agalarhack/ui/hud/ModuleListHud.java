package me.mrhakan.agalarhack.ui.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.managers.HudLayoutManager;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.ThemeService;
import me.mrhakan.agalarhack.ui.ClientUiTheme;
import me.mrhakan.agalarhack.ui.Hud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Uses exactly the same measured rectangle for drawing and HUD editor anchors. */
public final class ModuleListHud {
    private int width = 120, height = 12;
    private final RowAnimations animations = new RowAnimations();

    /*
     * Formatting/sorting/font measurement used to run once per rendered frame. Those values can
     * only change on a client tick, a setting change, or a screen-height change that changes the row
     * ceiling, so retain the settled model between frames and leave only animation/render work in
     * the render-rate path.
     */
    private final ArrayList<ModuleListModel.Entry> entryBuffer = new ArrayList<>(64);
    private final ArrayList<String> animationIds = new ArrayList<>(64);
    private final HashMap<String, RowAnimations.Row> motionById = new HashMap<>(64);
    private List<ModuleListModel.Row> rows = List.of();
    private int cachedTick = Integer.MIN_VALUE;
    private int cachedMaximum = Integer.MIN_VALUE;
    private int cachedFontIdentity = Integer.MIN_VALUE;
    private long cachedModelKey = Long.MIN_VALUE;
    private int cachedTotalEntries;
    private int cachedContentWidth;
    private int cachedCountWidth;
    private String cachedCountText = "";
    private boolean active;

    public int width() { return width; }
    public int height() { return height; }

    public void render(GuiGraphicsExtractor g, Minecraft mc) {
        var manager = AgalarHackClient.moduleManager;
        var options = manager.getModule("ModuleList");
        width = 120; height = mc.font.lineHeight;
        if (options == null || !options.isToggled()) {
            if (active) reset();
            active = false;
            return;
        }
        active = true;

        boolean background = options.getBooleanSetting("background", false);
        boolean sideBar = options.getBooleanSetting("sideBar", false);
        boolean showCount = options.getBooleanSetting("showCount", false);
        int padding = background || sideBar ? 4 : 0;
        int rowGap = (int) Math.round(options.getNumberSetting("rowGap", 0));
        int rowHeight = mc.font.lineHeight + (background ? 4 : 0) + rowGap;
        int headerHeight = showCount ? mc.font.lineHeight + 2 : 0;
        int maximum = Math.min((int) options.getNumberSetting("maximumRows", 32),
                Math.max(0, (g.guiHeight() - 8 - headerHeight) / Math.max(1, rowHeight)));

        refreshModel(manager.getModuleList(), options, maximum, mc);
        if (rows.isEmpty()) return;

        int contentWidth = showCount ? Math.max(cachedContentWidth, cachedCountWidth) : cachedContentWidth;
        width = Math.max(1, Math.min(Math.max(1, g.guiWidth() - 8), contentWidth + padding * 2));
        height = headerHeight + rows.size() * rowHeight - rowGap;
        var layout = AgalarHackClient.HUD_LAYOUT;
        int x = layout.resolveX("modules", g.guiWidth(), width), y = layout.resolveY("modules", g.guiHeight(), height);
        String alignment = options.getStringSetting("alignment", "Right");
        var anchor = layout.get("modules").anchor;
        boolean right = alignment.equals("Right") || alignment.equals("Anchor") &&
                (anchor == HudLayoutManager.Anchor.TOP_RIGHT || anchor == HudLayoutManager.Anchor.BOTTOM_RIGHT);
        boolean shadow = options.getBooleanSetting("textShadow", true);
        var theme = ClientServices.require(ThemeService.class).current();
        boolean animate = options.getBooleanSetting("rowAnimations", true) && theme.motionEnabled();
        double slide = (right ? 1 : -1) * options.getNumberSetting("slideDistance", 14);

        /*
         * Animation identity is the stable module id, not its display text. Dynamic suffixes such as
         * speed/mode readouts may change every tick; treating that text as identity made an unchanged
         * module fade out and back in whenever only its label changed.
         */
        var placed = animations.update(animationIds, rowHeight, slide,
                options.getNumberSetting("animationSpeed", 0.25), animate);
        motionById.clear();
        for (var motion : placed) motionById.put(motion.id(), motion);

        if (showCount) {
            int countX = right ? x + width - padding - cachedCountWidth : x + padding;
            g.text(mc.font, cachedCountText, countX, y, ClientUiTheme.MUTED, shadow);
        }

        int listY = y + headerHeight;
        int rainbowIndex = 0;
        for (var row : rows) {
            boolean overflow = ModuleListModel.isOverflow(row);
            int color;
            if (overflow) color = ClientUiTheme.MUTED;
            else if (theme.highContrast) color = ClientUiTheme.ACCENT;
            else color = switch (options.getStringSetting("colorMode", "Rainbow")) {
                case "Accent" -> ClientUiTheme.ACCENT;
                case "Category" -> categoryColor(row.category());
                default -> Hud.rainbow(++rainbowIndex * 300, theme);
            };

            var motion = motionById.get(row.id());
            int rowX = x + (motion == null ? 0 : (int) Math.round(motion.offsetX()));
            int rowY = listY + (motion == null ? 0 : (int) Math.round(motion.y()));
            int alpha = motion == null ? 255 : (int) Math.round(Math.max(0, Math.min(1, motion.alpha())) * 255);
            if (alpha <= 0) continue;
            String text = HudText.fitWithEllipsis(row.text(), Math.max(0, width - padding * 2), value -> mc.font.width(value),
                    (value, availableWidth) -> mc.font.plainSubstrByWidth(value, availableWidth));
            int visualHeight = rowHeight - rowGap;
            if (background) g.fill(rowX, rowY, rowX + width, rowY + visualHeight, fade(ClientUiTheme.PANEL, alpha));
            if (sideBar) {
                int edge = right ? rowX + width - 2 : rowX;
                g.fill(edge, rowY, edge + 2, rowY + visualHeight, fade(color, alpha));
            }
            int textX = right ? rowX + width - padding - mc.font.width(text) : rowX + padding;
            g.text(mc.font, text, textX, rowY + (background ? 2 : 0), fade(color, alpha), shadow);
        }
    }

    private void refreshModel(List<Module> modules, Module options, int maximum, Minecraft mc) {
        int tick = mc.player == null ? Integer.MIN_VALUE : mc.player.tickCount;
        long modelKey = modelKey(options, maximum);
        int fontIdentity = System.identityHashCode(mc.font);
        if (tick == cachedTick && maximum == cachedMaximum && modelKey == cachedModelKey
                && fontIdentity == cachedFontIdentity) return;

        entryBuffer.clear();
        for (Module module : modules) {
            if (module.isToggled() && module.getBooleanSetting("showInHud", true)) {
                entryBuffer.add(new ModuleListModel.Entry(
                        module.getName(), module.getDisplayName(), module.getCategory().name));
            }
        }

        rows = ModuleListModel.rows(entryBuffer,
                options.getStringSetting("displayMode", "Display"),
                options.getStringSetting("letterCase", "Normal"),
                options.getStringSetting("sorting", "Width"),
                maximum, mc.font::width, options.getBooleanSetting("overflowIndicator", true));

        animationIds.clear();
        int visibleModules = 0;
        int contentWidth = 0;
        for (var row : rows) {
            animationIds.add(row.id());
            contentWidth = Math.max(contentWidth, row.width());
            if (!ModuleListModel.isOverflow(row)) visibleModules++;
        }

        cachedTotalEntries = entryBuffer.size();
        cachedCountText = visibleModules == cachedTotalEntries
                ? cachedTotalEntries + " enabled"
                : visibleModules + "/" + cachedTotalEntries + " shown";
        cachedCountWidth = mc.font.width(cachedCountText);
        cachedContentWidth = contentWidth;
        cachedTick = tick;
        cachedMaximum = maximum;
        cachedModelKey = modelKey;
        cachedFontIdentity = fontIdentity;
    }

    private static long modelKey(Module options, int maximum) {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, options.getStringSetting("displayMode", "Display").hashCode());
        hash = mix(hash, options.getStringSetting("letterCase", "Normal").hashCode());
        hash = mix(hash, options.getStringSetting("sorting", "Width").hashCode());
        hash = mix(hash, options.getBooleanSetting("overflowIndicator", true) ? 1 : 0);
        return mix(hash, maximum);
    }

    private static long mix(long hash, int value) {
        return (hash ^ value) * 0x100000001b3L;
    }

    private void reset() {
        animations.clear();
        motionById.clear();
        entryBuffer.clear();
        animationIds.clear();
        rows = List.of();
        cachedTick = Integer.MIN_VALUE;
        cachedMaximum = Integer.MIN_VALUE;
        cachedFontIdentity = Integer.MIN_VALUE;
        cachedModelKey = Long.MIN_VALUE;
        cachedTotalEntries = 0;
        cachedContentWidth = 0;
        cachedCountWidth = 0;
        cachedCountText = "";
    }

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
