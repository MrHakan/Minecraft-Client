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

/** Searchable module browser with category filtering and first-party management tools. */
public class ClickGuiScreen extends Screen {
    private static final int ROW_HEIGHT = 24;

    private final String query;
    private final int page;
    private final int categoryIndex;
    private EditBox searchBox;
    private List<Module> visibleModules = List.of();
    private int pageCount = 1;
    private int safePage;

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

        int searchWidth = Math.max(90, Math.min(240, width - 150));
        searchBox = new EditBox(font, 12, 26, searchWidth, 20, Component.literal("Search modules/settings"));
        searchBox.setValue(query);
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal("Search"), b -> openSearch(searchBox.getValue(), 0, categoryIndex))
                .bounds(18 + searchWidth, 26, 58, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> openSearch("", 0, categoryIndex))
                .bounds(80 + searchWidth, 26, 52, 20).build());

        int navY = 50;
        int navWidth = Math.max(60, Math.min(94, (width - 24) / 4));
        int navStart = Math.max(8, (width - navWidth * 4) / 2);
        addRenderableWidget(Button.builder(Component.literal("Category: " + categoryName()), b ->
                openSearch(query, 0, (categoryIndex + 1) % (Category.values().length + 1)))
                .bounds(navStart, navY, navWidth - 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("HUD"), b -> minecraft.gui.setScreen(new HudEditorScreen(this)))
                .bounds(navStart + navWidth, navY, navWidth - 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Profiles"), b -> minecraft.gui.setScreen(new ProfileScreen(this)))
                .bounds(navStart + navWidth * 2, navY, navWidth - 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Targets"), b -> minecraft.gui.setScreen(new TargetPolicyScreen(this)))
                .bounds(navStart + navWidth * 3, navY, navWidth - 2, 20).build());

        List<Module> matches = query.isBlank()
                ? new ArrayList<>(AgalarHackClient.moduleManager.getModuleList())
                : new ArrayList<>(AgalarHackClient.moduleManager.searchModules(query));
        if (categoryIndex > 0) {
            Category wanted = Category.values()[categoryIndex - 1];
            matches.removeIf(module -> module.getCategory() != wanted);
        }

        int listTop = 82;
        int listBottom = Math.max(listTop + ROW_HEIGHT, height - 34);
        int rowsPerPage = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        pageCount = Math.max(1, (int) Math.ceil(matches.size() / (double) rowsPerPage));
        safePage = Math.min(page, pageCount - 1);
        int start = safePage * rowsPerPage;
        int end = Math.min(matches.size(), start + rowsPerPage);
        visibleModules = matches.subList(start, end);

        int rowWidth = Math.max(180, Math.min(470, width - 24));
        int rowX = (width - rowWidth) / 2;
        int settingsWidth = 78;
        int toggleWidth = Math.max(90, rowWidth - settingsWidth - 4);

        for (int i = 0; i < visibleModules.size(); i++) {
            Module module = visibleModules.get(i);
            int y = listTop + i * ROW_HEIGHT;
            String state = module.isToggled() ? "ON  " : "OFF ";
            addRenderableWidget(Button.builder(Component.literal(state + module.getName()), b -> {
                module.toggle();
                minecraft.gui.setScreen(new ClickGuiScreen(query, safePage, categoryIndex));
            }).bounds(rowX, y, toggleWidth, 20).build());

            addRenderableWidget(Button.builder(Component.literal("Settings"), b ->
                    minecraft.gui.setScreen(new ModuleSettingsScreen(this, module)))
                    .bounds(rowX + toggleWidth + 4, y, settingsWidth, 20).build());
        }

        if (safePage > 0) {
            addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> openSearch(query, safePage - 1, categoryIndex))
                    .bounds(12, height - 26, 64, 20).build());
        }
        if (safePage + 1 < pageCount) {
            addRenderableWidget(Button.builder(Component.literal("Next >"), b -> openSearch(query, safePage + 1, categoryIndex))
                    .bounds(width - 76, height - 26, 64, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds((width - 64) / 2, height - 26, 64, 20).build());
    }

    private void openSearch(String search, int targetPage, int targetCategory) {
        minecraft.gui.setScreen(new ClickGuiScreen(search == null ? "" : search.trim(), targetPage, targetCategory));
    }

    private String categoryName() {
        return categoryIndex == 0 ? "ALL" : Category.values()[categoryIndex - 1].name;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, "Agalar Hack Control Center", width / 2, 8, 0xFFFFFFFF);
        String status = categoryName() + " • " + (query.isBlank() ? "all modules" : "search: " + query)
                + " • page " + (safePage + 1) + "/" + pageCount;
        graphics.centeredText(font, status, width / 2, 72, 0xFFAAAAAA);
        if (visibleModules.isEmpty()) {
            graphics.centeredText(font, "No modules match this filter.", width / 2, 96, 0xFFFFAA55);
        }
    }
}
