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

/** Generic editor backed by Settings.SettingSpec metadata. */
public class ModuleSettingsScreen extends Screen {
    private static final int ROW_HEIGHT = 28;

    private final Screen parent;
    private final Module module;
    private final int requestedPage;
    private List<SettingSpec> visibleSpecs = List.of();
    private int safePage;
    private int pageCount = 1;
    private String feedback = "";
    private int feedbackColor = 0xFFAAAAAA;

    public ModuleSettingsScreen(Screen parent, Module module) {
        this(parent, module, 0);
    }

    private ModuleSettingsScreen(Screen parent, Module module, int page) {
        super(Component.literal(module.getName() + " Settings"));
        this.parent = parent;
        this.module = module;
        this.requestedPage = Math.max(0, page);
    }

    @Override
    public void init() {
        super.init();

        List<SettingSpec> editable = new ArrayList<>();
        for (SettingSpec spec : module.settings.getSpecs()) {
            if (!spec.getName().equals("enabled") && !spec.getName().equals("keybind")) {
                editable.add(spec);
            }
        }

        int listTop = 48;
        int listBottom = Math.max(listTop + ROW_HEIGHT, height - 42);
        int rowsPerPage = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        pageCount = Math.max(1, (int) Math.ceil(editable.size() / (double) rowsPerPage));
        safePage = Math.min(requestedPage, pageCount - 1);
        int start = safePage * rowsPerPage;
        int end = Math.min(editable.size(), start + rowsPerPage);
        visibleSpecs = editable.subList(start, end);

        int labelWidth = Math.max(90, Math.min(150, width / 3));
        int controlX = 20 + labelWidth;
        int saveWidth = 46;
        int controlWidth = Math.max(80, width - controlX - 20);

        for (int i = 0; i < visibleSpecs.size(); i++) {
            SettingSpec spec = visibleSpecs.get(i);
            int y = listTop + i * ROW_HEIGHT;
            Object current = module.settings.getSetting(spec.getName());

            if (spec.getType() == SettingType.BOOLEAN) {
                String value = Boolean.TRUE.equals(current) ? "ON" : "OFF";
                addRenderableWidget(Button.builder(Component.literal(value), button -> {
                    module.settings.setSetting(spec.getName(), !Boolean.TRUE.equals(module.settings.getSetting(spec.getName())));
                    persistAndRefresh();
                }).bounds(controlX, y, controlWidth, 20).build());
            } else if (spec.getType() == SettingType.CHOICE) {
                addRenderableWidget(Button.builder(Component.literal(String.valueOf(current)), button -> {
                    List<String> choices = spec.getChoices();
                    String now = String.valueOf(module.settings.getSetting(spec.getName()));
                    int index = 0;
                    for (int choiceIndex = 0; choiceIndex < choices.size(); choiceIndex++) {
                        if (choices.get(choiceIndex).equalsIgnoreCase(now)) {
                            index = choiceIndex;
                            break;
                        }
                    }
                    module.settings.setSetting(spec.getName(), choices.get((index + 1) % choices.size()));
                    persistAndRefresh();
                }).bounds(controlX, y, controlWidth, 20).build());
            } else {
                int inputWidth = Math.max(40, controlWidth - saveWidth - 4);
                EditBox editor = new EditBox(font, controlX, y, inputWidth, 20, Component.literal(spec.getName()));
                editor.setValue(String.valueOf(current));
                addRenderableWidget(editor);
                addRenderableWidget(Button.builder(Component.literal("Save"), button -> saveTextValue(spec, editor))
                        .bounds(controlX + inputWidth + 4, y, saveWidth, 20).build());
            }
        }

        if (safePage > 0) {
            addRenderableWidget(Button.builder(Component.literal("< Prev"), button ->
                    minecraft.gui.setScreen(new ModuleSettingsScreen(parent, module, safePage - 1)))
                    .bounds(16, height - 28, 64, 20).build());
        }
        if (safePage + 1 < pageCount) {
            addRenderableWidget(Button.builder(Component.literal("Next >"), button ->
                    minecraft.gui.setScreen(new ModuleSettingsScreen(parent, module, safePage + 1)))
                    .bounds(width - 80, height - 28, 64, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds((width - 64) / 2, height - 28, 64, 20).build());
    }

    private void saveTextValue(SettingSpec spec, EditBox editor) {
        try {
            Object parsed = module.settings.parseSettingValue(spec.getName(), editor.getValue().trim());
            module.settings.setSetting(spec.getName(), parsed);
            AgalarHackClient.SETTINGS_MANAGER.updateSettings();
            feedback = spec.getName() + " saved as " + parsed;
            feedbackColor = 0xFF55FF55;
        } catch (IllegalArgumentException e) {
            feedback = e.getMessage();
            feedbackColor = 0xFFFF5555;
        }
    }

    private void persistAndRefresh() {
        AgalarHackClient.SETTINGS_MANAGER.updateSettings();
        minecraft.gui.setScreen(new ModuleSettingsScreen(parent, module, safePage));
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, module.getName() + " Settings", width / 2, 10, 0xFFFFFFFF);
        graphics.centeredText(font, truncate(module.getDescription(), Math.max(80, width - 32)), width / 2, 25, 0xFFAAAAAA);

        int labelWidth = Math.max(90, Math.min(150, width / 3));
        for (int i = 0; i < visibleSpecs.size(); i++) {
            SettingSpec spec = visibleSpecs.get(i);
            int y = 48 + i * ROW_HEIGHT;
            String label = truncate(spec.getName() + " [" + spec.getConstraintText() + "]", labelWidth - 6);
            graphics.text(font, label, 16, y + 6, 0xFFFFFFFF);
        }

        if (!feedback.isBlank()) {
            graphics.centeredText(font, truncate(feedback, Math.max(80, width - 180)), width / 2, height - 40, feedbackColor);
        } else if (pageCount > 1) {
            graphics.centeredText(font, "Page " + (safePage + 1) + "/" + pageCount, width / 2, height - 40, 0xFFAAAAAA);
        }
    }

    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int allowed = Math.max(0, maxWidth - font.width(ellipsis));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String candidate = out.toString() + text.charAt(i);
            if (font.width(candidate) > allowed) {
                break;
            }
            out.append(text.charAt(i));
        }
        return out + ellipsis;
    }
}
