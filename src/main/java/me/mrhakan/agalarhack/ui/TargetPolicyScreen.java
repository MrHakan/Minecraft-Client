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
    private int feedbackColor = 0xFFAAAAAA;

    public TargetPolicyScreen(Screen parent) {
        super(Component.literal("Global Target Policy"));
        this.parent = parent;
    }

    @Override
    public void init() {
        super.init();
        Settings settings = AgalarHackClient.TARGET_POLICY.getSettings();
        List<SettingSpec> specs = new ArrayList<>(settings.getSpecs());
        int rowTop = 36;
        int rowHeight = 27;
        int labelWidth = Math.max(110, Math.min(170, width / 3));
        int controlX = 20 + labelWidth;
        int controlWidth = Math.max(90, width - controlX - 20);

        for (int i = 0; i < specs.size(); i++) {
            SettingSpec spec = specs.get(i);
            int y = rowTop + i * rowHeight;
            Object current = settings.getSetting(spec.getName());
            if (spec.getType() == SettingType.BOOLEAN) {
                addRenderableWidget(Button.builder(Component.literal(Boolean.TRUE.equals(current) ? "ON" : "OFF"), b -> {
                    settings.setSetting(spec.getName(), !Boolean.TRUE.equals(settings.getSetting(spec.getName())));
                    AgalarHackClient.TARGET_POLICY.save();
                    minecraft.gui.setScreen(new TargetPolicyScreen(parent));
                }).bounds(controlX, y, controlWidth, 20).build());
            } else {
                int inputWidth = Math.max(50, controlWidth - 50);
                EditBox box = new EditBox(font, controlX, y, inputWidth, 20, Component.literal(spec.getName()));
                box.setValue(String.valueOf(current));
                addRenderableWidget(box);
                addRenderableWidget(Button.builder(Component.literal("Save"), b -> save(spec, box))
                        .bounds(controlX + inputWidth + 4, y, 46, 20).build());
            }
        }

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds((width - 80) / 2, height - 28, 80, 20).build());
    }

    private void save(SettingSpec spec, EditBox box) {
        try {
            Settings settings = AgalarHackClient.TARGET_POLICY.getSettings();
            Object parsed = settings.parseSettingValue(spec.getName(), box.getValue().trim());
            settings.setSetting(spec.getName(), parsed);
            AgalarHackClient.TARGET_POLICY.save();
            feedback = spec.getName() + " saved as " + parsed;
            feedbackColor = 0xFF55FF55;
        } catch (IllegalArgumentException e) {
            feedback = e.getMessage();
            feedbackColor = 0xFFFF5555;
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, "Global Target Policy", width / 2, 10, 0xFFFFFFFF);
        graphics.centeredText(font, "Applied before module-specific range/FOV/timing filters", width / 2, 22, 0xFFAAAAAA);

        List<SettingSpec> specs = new ArrayList<>(AgalarHackClient.TARGET_POLICY.getSettings().getSpecs());
        int labelWidth = Math.max(110, Math.min(170, width / 3));
        for (int i = 0; i < specs.size(); i++) {
            SettingSpec spec = specs.get(i);
            graphics.text(font, truncate(spec.getName() + " [" + spec.getConstraintText() + "]", labelWidth - 6),
                    16, 42 + i * 27, 0xFFFFFFFF);
        }
        if (!feedback.isBlank()) {
            graphics.centeredText(font, feedback, width / 2, height - 42, feedbackColor);
        }
    }

    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        while (text.length() > 1 && font.width(text + "...") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }
}
