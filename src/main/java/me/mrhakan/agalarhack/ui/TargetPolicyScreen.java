package me.mrhakan.agalarhack.ui;

import java.util.ArrayList;
import java.util.List;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.managers.Settings;
import me.mrhakan.agalarhack.managers.Settings.SettingSpec;
import me.mrhakan.agalarhack.managers.Settings.SettingType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Editor for the shared target filter consumed by combat and target-aware render modules. */
public class TargetPolicyScreen extends Screen {
    private final Screen parent;
    private String feedback = "";
    private int feedbackColor = ClientUiTheme.MUTED;

    public TargetPolicyScreen(Screen parent) { super(Component.literal("Global Target Policy")); this.parent = parent; }

    @Override
    public void init() {
        super.init();
        Settings settings = AgalarHackClient.TARGET_POLICY.getSettings();
        List<SettingSpec> specs = new ArrayList<>(settings.getSpecs());
        int rowTop = 48, rowHeight = 30;
        int panelWidth = Math.min(520, width - 24), panelX = (width - panelWidth) / 2;
        int controlWidth = Math.max(100, panelWidth / 2 - 16), controlX = panelX + panelWidth - controlWidth - 8;
        for (int i = 0; i < specs.size(); i++) {
            SettingSpec spec = specs.get(i); int y = rowTop + i * rowHeight; Object current = settings.getSetting(spec.getName());
            if (spec.getType() == SettingType.BOOLEAN) {
                addRenderableWidget(Button.builder(Component.literal(Boolean.TRUE.equals(current) ? "ON" : "OFF"), b -> {
                    settings.setSetting(spec.getName(), !Boolean.TRUE.equals(settings.getSetting(spec.getName())));
                    AgalarHackClient.TARGET_POLICY.save(); minecraft.gui.setScreen(new TargetPolicyScreen(parent));
                }).bounds(controlX, y + 4, controlWidth, 20).build());
            } else {
                int inputWidth = Math.max(50, controlWidth - 50);
                EditBox box = new EditBox(font, controlX, y + 4, inputWidth, 20, Component.literal(spec.getName())); box.setValue(String.valueOf(current)); addRenderableWidget(box);
                addRenderableWidget(Button.builder(Component.literal("Save"), b -> save(spec, box)).bounds(controlX + inputWidth + 4, y + 4, 46, 20).build());
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds((width - 80) / 2, height - 28, 80, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ClientUiTheme.backdrop(graphics, width, height);
        int panelWidth = Math.min(520, width - 24), panelX = (width - panelWidth) / 2;
        List<SettingSpec> specs = new ArrayList<>(AgalarHackClient.TARGET_POLICY.getSettings().getSpecs());
        for (int i = 0; i < specs.size(); i++) ClientUiTheme.panel(graphics, panelX, 48 + i * 30, panelWidth, 26, false);
    }

    private void save(SettingSpec spec, EditBox box) {
        try {
            Settings settings = AgalarHackClient.TARGET_POLICY.getSettings(); Object parsed = settings.parseSettingValue(spec.getName(), box.getValue().trim());
            settings.setSetting(spec.getName(), parsed); AgalarHackClient.TARGET_POLICY.save(); feedback = spec.getName() + " saved as " + parsed; feedbackColor = ClientUiTheme.SUCCESS;
        } catch (IllegalArgumentException e) { feedback = e.getMessage(); feedbackColor = ClientUiTheme.DANGER; }
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, "GLOBAL TARGET POLICY", width / 2, 11, ClientUiTheme.TEXT);
        graphics.centeredText(font, "Shared first-pass filtering before module-specific range / FOV / timing", width / 2, 25, ClientUiTheme.MUTED);
        List<SettingSpec> specs = new ArrayList<>(AgalarHackClient.TARGET_POLICY.getSettings().getSpecs());
        int panelWidth = Math.min(520, width - 24), panelX = (width - panelWidth) / 2;
        for (int i = 0; i < specs.size(); i++) {
            SettingSpec spec = specs.get(i); int y = 48 + i * 30;
            graphics.text(font, spec.getName(), panelX + 8, y + 5, ClientUiTheme.TEXT, true);
            graphics.text(font, truncate(spec.getConstraintText(), 145), panelX + 8, y + 16, ClientUiTheme.MUTED, false);
        }
        if (!feedback.isBlank()) graphics.centeredText(font, feedback, width / 2, height - 42, feedbackColor);
    }

    private String truncate(String text, int maxWidth) { if (font.width(text) <= maxWidth) return text; while (text.length() > 1 && font.width(text + "…") > maxWidth) text = text.substring(0, text.length() - 1); return text + "…"; }
}
