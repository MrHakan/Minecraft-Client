package me.mrhakan.agalarhack.ui;

import java.util.List;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.managers.HudLayoutManager.WidgetState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native-widget HUD editor for anchor, visibility and offset control. */
public class HudEditorScreen extends Screen {
    private static final List<String> WIDGETS = List.of("branding", "modules", "info", "target");

    private final Screen parent;
    private final String selected;

    public HudEditorScreen(Screen parent) {
        this(parent, "branding");
    }

    private HudEditorScreen(Screen parent, String selected) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
        this.selected = WIDGETS.contains(selected) ? selected : "branding";
    }

    @Override
    public void init() {
        super.init();
        int center = width / 2;
        int y = 30;
        int buttonWidth = Math.max(70, Math.min(110, (width - 40) / 4));
        int totalWidth = buttonWidth * WIDGETS.size();
        int startX = Math.max(8, center - totalWidth / 2);

        for (int i = 0; i < WIDGETS.size(); i++) {
            String id = WIDGETS.get(i);
            addRenderableWidget(Button.builder(Component.literal((id.equals(selected) ? "> " : "") + title(id)), button ->
                    minecraft.gui.setScreen(new HudEditorScreen(parent, id)))
                    .bounds(startX + i * buttonWidth, y, buttonWidth - 2, 20).build());
        }

        WidgetState state = AgalarHackClient.HUD_LAYOUT.get(selected);
        addRenderableWidget(Button.builder(Component.literal("Anchor: " + state.anchor), button -> {
            AgalarHackClient.HUD_LAYOUT.cycleAnchor(selected);
            refresh();
        }).bounds(center - 100, 68, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Visible: " + (state.visible ? "ON" : "OFF")), button -> {
            AgalarHackClient.HUD_LAYOUT.toggleVisible(selected);
            refresh();
        }).bounds(center - 100, 92, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("↑"), button -> move(0, -5))
                .bounds(center - 20, 124, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("←"), button -> move(-5, 0))
                .bounds(center - 64, 148, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("→"), button -> move(5, 0))
                .bounds(center + 24, 148, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↓"), button -> move(0, 5))
                .bounds(center - 20, 172, 40, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Reset widget"), button -> {
            AgalarHackClient.HUD_LAYOUT.reset(selected);
            refresh();
        }).bounds(center - 100, 208, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset all"), button -> {
            AgalarHackClient.HUD_LAYOUT.resetAll();
            refresh();
        }).bounds(center + 4, 208, 96, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(center - 40, height - 28, 80, 20).build());
    }

    private void move(int dx, int dy) {
        AgalarHackClient.HUD_LAYOUT.move(selected, dx, dy);
        refresh();
    }

    private void refresh() {
        minecraft.gui.setScreen(new HudEditorScreen(parent, selected));
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        WidgetState state = AgalarHackClient.HUD_LAYOUT.get(selected);
        graphics.centeredText(font, "HUD Editor", width / 2, 10, 0xFFFFFFFF);
        graphics.centeredText(font, "Selected: " + title(selected), width / 2, 54, 0xFFAAAAAA);
        graphics.centeredText(font, "Offset X/Y: " + state.offsetX + " / " + state.offsetY,
                width / 2, 194, 0xFFAAAAAA);
        graphics.centeredText(font, "Changes are saved immediately. Use arrows to move 5px at a time.",
                width / 2, 236, 0xFF777777);
    }

    private String title(String id) {
        return switch (id) {
            case "branding" -> "Branding";
            case "modules" -> "Module List";
            case "info" -> "Info";
            case "target" -> "Target HUD";
            default -> id;
        };
    }
}
