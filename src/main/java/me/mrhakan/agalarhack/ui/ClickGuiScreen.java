package me.mrhakan.agalarhack.ui;

import java.util.ArrayList;
import java.util.List;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Modern searchable control center with persistent category sidebar and module cards. */
public class ClickGuiScreen extends Screen implements me.mrhakan.agalarhack.ui.ClientScreen {
    private static final int SIDEBAR_WIDTH = 112;
    private static final int HEADER_HEIGHT = 82;
    private static final int ROW_HEIGHT = 40;

    private static String filter = "All";
    private static String sort = "Name";
    private final String query;
    private final int page;
    private final int categoryIndex;
    private EditBox searchBox;
    private List<Module> visibleModules = List.of();
    private final List<RowVisual> rowVisuals = new ArrayList<>();
    private int pageCount = 1;
    private int safePage;
    private int enabledCount;

    public ClickGuiScreen() {
        this("", 0, 0);
    }

    private ClickGuiScreen(String query, int page, int categoryIndex) {
        super(Component.literal("Agalar Hack"));
        this.query = query == null ? "" : query;
        this.page = Math.max(0, page);
        this.categoryIndex = Math.max(0, Math.min(Category.values().length, categoryIndex));
    }

    @Override
    public void init() {
        super.init();
        rowVisuals.clear();
        enabledCount = (int) AgalarHackClient.moduleManager.getModuleList().stream().filter(Module::isToggled).count();

        int contentLeft = SIDEBAR_WIDTH + 14;
        int contentRight = width - 14;
        int contentWidth = Math.max(180, contentRight - contentLeft);
        int searchWidth = Math.max(50, contentWidth - 125);
        searchBox = new EditBox(font, contentLeft, 26, searchWidth, 20, Component.literal("Search modules & settings"));
        searchBox.setValue(query);
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal("Search"), b -> openSearch(searchBox.getValue(), 0, categoryIndex))
                .bounds(contentLeft + searchWidth + 5, 26, 58, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> openSearch("", 0, categoryIndex))
                .bounds(contentLeft + searchWidth + 67, 26, 50, 20).build());

        int actionWidth = Math.max(30,(contentWidth-16)/5);
        addRenderableWidget(Button.builder(Component.literal("Targets"), b -> minecraft.gui.setScreen(new TargetPolicyScreen(this)))
                .bounds(contentLeft,50,actionWidth,20).build());
        addRenderableWidget(Button.builder(Component.literal("Profiles"), b -> minecraft.gui.setScreen(new ProfileScreen(this)))
                .bounds(contentLeft+actionWidth+4,50,actionWidth,20).build());
        addRenderableWidget(Button.builder(Component.literal("HUD"), b -> minecraft.gui.setScreen(new HudEditorScreen(this)))
                .bounds(contentLeft+(actionWidth+4)*2,50,actionWidth,20).build());
        addRenderableWidget(Button.builder(Component.literal("Filter"), b -> minecraft.gui.setScreen(new me.mrhakan.agalarhack.ui.components.ChoiceScreen(this,"Filter",List.of("All","Enabled","Bound","Favorites","Recent"),choice->filter=choice)))
                .bounds(contentLeft+(actionWidth+4)*3,50,actionWidth,20).build());
        addRenderableWidget(Button.builder(Component.literal("Sort"), b -> minecraft.gui.setScreen(new me.mrhakan.agalarhack.ui.components.ChoiceScreen(this,"Sort",List.of("Name","Category","Recent"),choice->sort=choice)))
                .bounds(contentLeft+(actionWidth+4)*4,50,actionWidth,20).build());

        addRenderableWidget(Button.builder(Component.literal("Themes"),b->minecraft.gui.setScreen(new ThemeScreen(this))).bounds(10,height-28,SIDEBAR_WIDTH-20,20).build());
        int categoryY = 62;
        // The sidebar must stay above the Themes button at every GUI scale, so the row pitch
        // shrinks once the category list no longer fits instead of overlapping it.
        int entries = Category.values().length + 1;
        int sidebarSpace = Math.max(entries * 12, height - 28 - 6 - categoryY);
        int step = Math.max(12, Math.min(24, sidebarSpace / entries));
        int buttonHeight = Math.max(9, step - 4);
        addCategoryButton("ALL", 0, categoryY, buttonHeight);
        for (int i = 0; i < Category.values().length; i++) {
            addCategoryButton(Category.values()[i].name, i + 1, categoryY + (i + 1) * step, buttonHeight);
        }

        List<Module> matches = query.isBlank()
                ? new ArrayList<>(AgalarHackClient.moduleManager.getModuleList())
                : new ArrayList<>(AgalarHackClient.moduleManager.searchModules(query));
        if (categoryIndex > 0) {
            Category wanted = Category.values()[categoryIndex - 1];
            matches.removeIf(module -> module.getCategory() != wanted);
        }

        matches.removeIf(module -> switch(filter) {
            case "Enabled" -> !module.isToggled(); case "Bound" -> module.getKey()==-1;
            case "Favorites" -> !module.getBooleanSetting("favorite",false);
            case "Recent" -> module.getNumberSetting("lastUsed",0)==0; default -> false;
        });
        java.util.Comparator<Module> byName=java.util.Comparator.comparing(Module::getName,String.CASE_INSENSITIVE_ORDER);
        matches.sort(switch(sort){case "Category" -> java.util.Comparator.comparing((Module m)->m.getCategory().name).thenComparing(byName);
            case "Recent" -> java.util.Comparator.comparingDouble((Module m)->m.getNumberSetting("lastUsed",0)).reversed().thenComparing(byName);default->byName;});
        int listTop = HEADER_HEIGHT + 12;
        int listBottom = Math.max(listTop + ROW_HEIGHT, height - 35);
        int rowsPerPage = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        pageCount = Math.max(1, (int) Math.ceil(matches.size() / (double) rowsPerPage));
        safePage = Math.min(page, pageCount - 1);
        int start = safePage * rowsPerPage;
        int end = Math.min(matches.size(), start + rowsPerPage);
        visibleModules = matches.subList(start, end);

        for (int i = 0; i < visibleModules.size(); i++) {
            Module module = visibleModules.get(i);
            int y = listTop + i * ROW_HEIGHT;
            int cardWidth = contentWidth;
            rowVisuals.add(new RowVisual(contentLeft, y, cardWidth, ROW_HEIGHT - 4, module));
            int settingsWidth = 70;
            int toggleWidth = 66;
            addRenderableWidget(Button.builder(Component.literal(module.isToggled() ? "ON" : "OFF"), b -> {
                module.toggle();
                minecraft.gui.setScreen(new ClickGuiScreen(searchBox.getValue(), safePage, categoryIndex));
            }).bounds(contentRight - settingsWidth - toggleWidth - 8, y + 7, toggleWidth, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Settings"), b ->
                    minecraft.gui.setScreen(new ModuleSettingsScreen(this, module)))
                    .bounds(contentRight - settingsWidth, y + 7, settingsWidth, 20).build());
        }

        if (safePage > 0) {
            addRenderableWidget(Button.builder(Component.literal("‹ Prev"), b -> openSearch(searchBox.getValue(), safePage - 1, categoryIndex))
                    .bounds(contentLeft, height - 27, 62, 20).build());
        }
        if (safePage + 1 < pageCount) {
            addRenderableWidget(Button.builder(Component.literal("Next ›"), b -> openSearch(searchBox.getValue(), safePage + 1, categoryIndex))
                    .bounds(contentRight - 62, height - 27, 62, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds((contentLeft + contentRight) / 2 - 30, height - 27, 60, 20).build());
    }

    private void addCategoryButton(String label, int index, int y, int buttonHeight) {
        String prefix = categoryIndex == index ? "• " : "  ";
        addRenderableWidget(Button.builder(Component.literal(prefix + label), b -> openSearch(searchBox.getValue(), 0, index))
                .bounds(10, y, SIDEBAR_WIDTH - 20, buttonHeight).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ClientUiTheme.backdrop(graphics, width, height);
        graphics.fill(0, 2, SIDEBAR_WIDTH, height, ClientUiTheme.SIDEBAR);
        graphics.fill(SIDEBAR_WIDTH, 2, width, HEADER_HEIGHT, ClientUiTheme.SIDEBAR);
        graphics.fill(SIDEBAR_WIDTH - 1, 2, SIDEBAR_WIDTH, height, ClientUiTheme.BORDER);
        for (RowVisual row : rowVisuals) {
            ClientUiTheme.panel(graphics, row.x, row.y, row.width, row.height, row.module.isToggled());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(font, "AGALAR", 16, 14, ClientUiTheme.TEXT, true);
        graphics.text(font, "HACK", 16, 26, ClientUiTheme.ACCENT, true);
        graphics.text(font, "v" + AgalarHackClient.VERSION, 16, 40, ClientUiTheme.MUTED, false);
        graphics.text(font, "CONTROL CENTER", SIDEBAR_WIDTH + 14, 9, ClientUiTheme.TEXT, true);
        graphics.text(font, enabledCount + " active  •  " + categoryName() + "  •  " + filter + "  •  page " + (safePage + 1) + "/" + pageCount,
                SIDEBAR_WIDTH + 14, 74, ClientUiTheme.MUTED, false);

        for (RowVisual row : rowVisuals) {
            int nameColor = row.module.isToggled() ? ClientUiTheme.SUCCESS : ClientUiTheme.TEXT;
            graphics.text(font, (row.module.getBooleanSetting("favorite",false)?"★ ":"") + row.module.getName(), row.x + 10, row.y + 7, nameColor, true);
            String desc = truncate(row.module.getDescription(), Math.max(60, row.width - 175));
            graphics.text(font, desc, row.x + 10, row.y + 21, ClientUiTheme.MUTED, false);
        }
        if (visibleModules.isEmpty()) {
            graphics.centeredText(font, "No modules match this filter.", (SIDEBAR_WIDTH + width) / 2, 90, 0xFFFFB86B);
        }
    }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean doubleClick) {
        if(event.button()==1) for(var row:rowVisuals) if(event.x()>=row.x && event.x()<row.x+row.width && event.y()>=row.y && event.y()<row.y+row.height) {
            minecraft.gui.setScreen(new ModuleActionsScreen(this,row.module));return true;
        }
        return super.mouseClicked(event,doubleClick);
    }

    private void openSearch(String search, int targetPage, int targetCategory) {
        minecraft.gui.setScreen(new ClickGuiScreen(search == null ? "" : search.trim(), targetPage, targetCategory));
    }

    private String categoryName() {
        return categoryIndex == 0 ? "ALL" : Category.values()[categoryIndex - 1].name;
    }

    private String truncate(String text, int maxWidth) {
        if (text == null || text.isBlank()) return "No description";
        if (font.width(text) <= maxWidth) return text;
        String suffix = "…";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (font.width(out.toString() + text.charAt(i) + suffix) > maxWidth) break;
            out.append(text.charAt(i));
        }
        return out + suffix;
    }

    private record RowVisual(int x, int y, int width, int height, Module module) {}
}
