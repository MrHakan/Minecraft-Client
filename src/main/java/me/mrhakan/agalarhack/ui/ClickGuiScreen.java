package me.mrhakan.agalarhack.ui;

import java.util.List;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Lightweight vanilla-widget ClickGUI. It deliberately reuses Minecraft's Screen,
 * Button and EditBox widgets instead of maintaining a parallel rendering/input
 * framework, keeping it resilient across game updates and accessible at small UI
 * scales.
 */
public class ClickGuiScreen extends Screen {
    private static final int ROW_HEIGHT = 24;

    private final String query;
    private final int page;
    private EditBox searchBox;
    private List<Module> visibleModules = List.of();
    private int pageCount = 1;

    public ClickGuiScreen() {
        this("", 0);
    }

    private ClickGuiScreen(String query, int page) {
        super(Component.literal("Agalar Hack"));
        this.query = query == null ? "" : query;
        this.page = Math.max(0, page);
    }

    @Override
    public void init() {
        super.init();

        int searchWidth = Math.max(100, Math.min(260, width - 126));
        searchBox = new EditBox(font, 16, 28, searchWidth, 20, Component.literal("Search modules"));
        searchBox.setValue(query);
        addRenderableWidget(searchBox);

        int searchButtonX = 22 + searchWidth;
        addRenderableWidget(Button.builder(Component.literal("Search"), button -> openSearch(searchBox.getValue(), 0))
                .bounds(searchButtonX, 28, 62, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), button -> openSearch("", 0))
                .bounds(searchButtonX + 66, 28, 54, 20).build());

        List<Module> matches = query.isBlank()
                ? AgalarHackClient.moduleManager.getModuleList()
                : AgalarHackClient.moduleManager.searchModules(query);

        int listTop = 58;
        int listBottom = Math.max(listTop + ROW_HEIGHT, height - 38);
        int rowsPerPage = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        pageCount = Math.max(1, (int) Math.ceil(matches.size() / (double) rowsPerPage));
        int safePage = Math.min(page, pageCount - 1);
        int start = safePage * rowsPerPage;
        int end = Math.min(matches.size(), start + rowsPerPage);
        visibleModules = matches.subList(start, end);

        int rowWidth = Math.max(160, Math.min(430, width - 32));
        int rowX = (width - rowWidth) / 2;
        int settingsWidth = 76;
        int toggleWidth = Math.max(80, rowWidth - settingsWidth - 4);

        for (int i = 0; i < visibleModules.size(); i++) {
            Module module = visibleModules.get(i);
            int y = listTop + i * ROW_HEIGHT;
            String state = module.isToggled() ? "ON  " : "OFF ";
            addRenderableWidget(Button.builder(Component.literal(state + module.getName()), button -> {
                module.toggle();
                minecraft.gui.setScreen(new ClickGuiScreen(query, safePage));
            }).bounds(rowX, y, toggleWidth, 20).build());

            addRenderableWidget(Button.builder(Component.literal("Settings"), button ->
                    minecraft.gui.setScreen(new ModuleSettingsScreen(this, module)))
                    .bounds(rowX + toggleWidth + 4, y, settingsWidth, 20).build());
        }

        if (safePage > 0) {
            addRenderableWidget(Button.builder(Component.literal("< Prev"), button -> openSearch(query, safePage - 1))
                    .bounds(16, height - 28, 64, 20).build());
        }
        if (safePage + 1 < pageCount) {
            addRenderableWidget(Button.builder(Component.literal("Next >"), button -> openSearch(query, safePage + 1))
                    .bounds(width - 80, height - 28, 64, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds((width - 64) / 2, height - 28, 64, 20).build());
    }

    private void openSearch(String search, int targetPage) {
        minecraft.gui.setScreen(new ClickGuiScreen(search == null ? "" : search.trim(), targetPage));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, "Agalar Hack Modules", width / 2, 10, 0xFFFFFFFF);
        String status = query.isBlank()
                ? visibleModules.size() + " visible module(s)"
                : "Search: " + query + " — " + visibleModules.size() + " on this page";
        graphics.text(font, status, 16, 51, 0xFFAAAAAA);
        if (pageCount > 1) {
            graphics.centeredText(font, "Page " + (Math.min(page, pageCount - 1) + 1) + "/" + pageCount,
                    width / 2, height - 42, 0xFFAAAAAA);
        }
    }
}
