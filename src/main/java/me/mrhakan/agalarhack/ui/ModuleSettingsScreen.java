package me.mrhakan.agalarhack.ui;

import java.util.ArrayList;
import java.util.List;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.managers.Settings.SettingSpec;
import me.mrhakan.agalarhack.managers.Settings.SettingType;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Generic typed settings editor using the same visual language as the Control Center. */
public class ModuleSettingsScreen extends Screen {
    private static final int ROW_HEIGHT = 32;
    private final Screen parent;
    private final Module module;
    private final int requestedPage;
    private List<SettingSpec> visibleSpecs = List.of();
    private final List<RowVisual> rows = new ArrayList<>();
    private int safePage;
    private int pageCount = 1;
    private String feedback = "";
    private int feedbackColor = ClientUiTheme.MUTED;

    public ModuleSettingsScreen(Screen parent, Module module) { this(parent, module, 0); }

    private ModuleSettingsScreen(Screen parent, Module module, int page) {
        super(Component.literal(module.getName() + " Settings"));
        this.parent = parent;
        this.module = module;
        this.requestedPage = Math.max(0, page);
    }

    @Override
    public void init() {
        super.init();
        rows.clear();
        List<SettingSpec> editable = new ArrayList<>();
        for (SettingSpec spec : module.settings.getSpecs()) {
            if (!spec.getName().equals("enabled") && !spec.getName().equals("keybind") && !spec.getName().equals("keyModifiers")) editable.add(spec);
        }
        int panelX = Math.max(12, width / 2 - Math.min(310, width / 2 - 12));
        int panelWidth = Math.min(620, width - panelX * 2);
        addRenderableWidget(Button.builder(Component.literal("Bind: " + module.getBindLabel()), b -> minecraft.gui.setScreen(new KeybindCaptureScreen(this,module)))
                .bounds(Math.max(12,width/2-110),37,220,18).build());
        int listTop = 58;
        int listBottom = Math.max(listTop + ROW_HEIGHT, height - 42);
        int rowsPerPage = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        pageCount = Math.max(1, (int) Math.ceil(editable.size() / (double) rowsPerPage));
        safePage = Math.min(requestedPage, pageCount - 1);
        int start = safePage * rowsPerPage;
        int end = Math.min(editable.size(), start + rowsPerPage);
        visibleSpecs = editable.subList(start, end);
        int controlWidth = Math.max(100, panelWidth / 2 - 18);
        int controlX = panelX + panelWidth - controlWidth - 8;

        for (int i = 0; i < visibleSpecs.size(); i++) {
            SettingSpec spec = visibleSpecs.get(i);
            int y = listTop + i * ROW_HEIGHT;
            rows.add(new RowVisual(panelX, y, panelWidth, ROW_HEIGHT - 4));
            Object current = module.settings.getSetting(spec.getName());
            if (spec.getType() == SettingType.BOOLEAN) {
                addRenderableWidget(Button.builder(Component.literal(Boolean.TRUE.equals(current) ? "ON" : "OFF"), button -> {
                    module.settings.setSetting(spec.getName(), !Boolean.TRUE.equals(module.settings.getSetting(spec.getName())));
                    persistAndRefresh();
                }).bounds(controlX, y + 4, controlWidth, 20).build());
            } else if (spec.getType() == SettingType.CHOICE) {
                addRenderableWidget(Button.builder(Component.literal(String.valueOf(current)), button -> {
                    List<String> choices = spec.getChoices();
                    String now = String.valueOf(module.settings.getSetting(spec.getName()));
                    int index = 0;
                    for (int c = 0; c < choices.size(); c++) if (choices.get(c).equalsIgnoreCase(now)) { index = c; break; }
                    module.settings.setSetting(spec.getName(), choices.get((index + 1) % choices.size()));
                    persistAndRefresh();
                }).bounds(controlX, y + 4, controlWidth, 20).build());
            } else {
                int saveWidth = 46;
                int inputWidth = Math.max(40, controlWidth - saveWidth - 4);
                EditBox editor = new EditBox(font, controlX, y + 4, inputWidth, 20, Component.literal(spec.getName()));
                editor.setValue(String.valueOf(current));
                addRenderableWidget(editor);
                addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveTextValue(spec, editor))
                        .bounds(controlX + inputWidth + 4, y + 4, saveWidth, 20).build());
            }
        }
        if (safePage > 0) addRenderableWidget(Button.builder(Component.literal("‹ Prev"), b -> minecraft.gui.setScreen(new ModuleSettingsScreen(parent, module, safePage - 1))).bounds(16, height - 28, 64, 20).build());
        if (safePage + 1 < pageCount) addRenderableWidget(Button.builder(Component.literal("Next ›"), b -> minecraft.gui.setScreen(new ModuleSettingsScreen(parent, module, safePage + 1))).bounds(width - 80, height - 28, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds((width - 64) / 2, height - 28, 64, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ClientUiTheme.backdrop(graphics, width, height);
        for (RowVisual row : rows) ClientUiTheme.panel(graphics, row.x, row.y, row.width, row.height, false);
    }

    private void saveTextValue(SettingSpec spec, EditBox editor) {
        try {
            Object parsed = module.settings.parseSettingValue(spec.getName(), editor.getValue().trim());
            module.settings.setSetting(spec.getName(), parsed);
            AgalarHackClient.SETTINGS_MANAGER.updateSettings();
            feedback = spec.getName() + " saved as " + parsed;
            feedbackColor = ClientUiTheme.SUCCESS;
        } catch (IllegalArgumentException e) {
            feedback = e.getMessage();
            feedbackColor = ClientUiTheme.DANGER;
        }
    }

    private void persistAndRefresh() {
        AgalarHackClient.SETTINGS_MANAGER.updateSettings();
        minecraft.gui.setScreen(new ModuleSettingsScreen(parent, module, safePage));
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, module.getName().toUpperCase() + " SETTINGS", width / 2, 11, ClientUiTheme.TEXT);
        graphics.centeredText(font, truncate(module.getDescription(), Math.max(80, width - 32)), width / 2, 27, ClientUiTheme.MUTED);
        int panelX = rows.isEmpty() ? 16 : rows.get(0).x;
        for (int i = 0; i < visibleSpecs.size(); i++) {
            SettingSpec spec = visibleSpecs.get(i);
            int y = 58 + i * ROW_HEIGHT;
            graphics.text(font, spec.getName(), panelX + 9, y + 5, ClientUiTheme.TEXT, true);
            graphics.text(font, truncate(spec.getConstraintText(), 145), panelX + 9, y + 17, ClientUiTheme.MUTED, false);
        }
        if (!feedback.isBlank()) graphics.centeredText(font, truncate(feedback, Math.max(80, width - 180)), width / 2, height - 40, feedbackColor);
        else if (pageCount > 1) graphics.centeredText(font, "Page " + (safePage + 1) + "/" + pageCount, width / 2, height - 40, ClientUiTheme.MUTED);
    }

    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String suffix = "…";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (font.width(out.toString() + text.charAt(i) + suffix) > maxWidth) break;
            out.append(text.charAt(i));
        }
        return out + suffix;
    }

    private record RowVisual(int x, int y, int width, int height) {}
}
