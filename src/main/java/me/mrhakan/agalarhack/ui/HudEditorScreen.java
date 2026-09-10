package me.mrhakan.agalarhack.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.managers.HudLayoutManager.WidgetState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag-and-drop HUD editor with anchor-aware persistent placement. */
public class HudEditorScreen extends Screen {
    private static final List<String> WIDGETS = List.of("branding", "modules", "info", "target");

    private final Screen parent;
    private String selected;
    private String dragging;
    private double grabX;
    private double grabY;
    private final Map<String, Bounds> bounds = new LinkedHashMap<>();

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
        int bottom = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Anchor"), button -> {
            AgalarHackClient.HUD_LAYOUT.cycleAnchor(selected);
        }).bounds(8, bottom, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Visible"), button -> {
            AgalarHackClient.HUD_LAYOUT.toggleVisible(selected);
        }).bounds(72, bottom, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            AgalarHackClient.HUD_LAYOUT.reset(selected);
        }).bounds(136, bottom, 54, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset all"), button -> {
            AgalarHackClient.HUD_LAYOUT.resetAll();
        }).bounds(194, bottom, 66, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(width - 58, bottom, 50, 20).build());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != 0 || event.y() >= height - 34) {
            return false;
        }

        updateBounds();
        for (int i = WIDGETS.size() - 1; i >= 0; i--) {
            String id = WIDGETS.get(i);
            Bounds b = bounds.get(id);
            if (b != null && b.contains(event.x(), event.y())) {
                selected = id;
                dragging = id;
                grabX = event.x() - b.x;
                grabY = event.y() - b.y;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging == null || event.button() != 0) {
            return super.mouseDragged(event, dragX, dragY);
        }
        Bounds b = boundsFor(dragging);
        int x = (int) Math.round(event.x() - grabX);
        int y = (int) Math.round(event.y() - grabY);
        AgalarHackClient.HUD_LAYOUT.moveTo(dragging, x, y, width, height, b.width, b.height, false, false);
        updateBounds();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null && event.button() == 0) {
            Bounds b = boundsFor(dragging);
            int x = (int) Math.round(event.x() - grabX);
            int y = (int) Math.round(event.y() - grabY);
            AgalarHackClient.HUD_LAYOUT.moveTo(dragging, x, y, width, height, b.width, b.height, true, true);
            dragging = null;
            updateBounds();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        if (dragging != null) {
            AgalarHackClient.HUD_LAYOUT.save();
            dragging = null;
        }
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        updateBounds();

        graphics.centeredText(font, "HUD Editor — drag widgets to reposition", width / 2, 8, 0xFFFFFFFF);
        graphics.centeredText(font, "Drop near a corner to auto-anchor. Selected: " + title(selected),
                width / 2, 20, 0xFFAAAAAA);

        for (String id : WIDGETS) {
            Bounds b = bounds.get(id);
            WidgetState state = AgalarHackClient.HUD_LAYOUT.get(id);
            boolean active = id.equals(selected);
            int fill = active ? 0x804477AA : 0x60303030;
            int border = active ? 0xFF77BBFF : 0xFF777777;
            if (!state.visible) {
                fill = active ? 0x603F3040 : 0x40202020;
            }
            graphics.fill(b.x, b.y, b.x + b.width, b.y + b.height, fill);
            graphics.outline(b.x, b.y, b.width, b.height, border);
            graphics.text(font, title(id) + (state.visible ? "" : " [hidden]"), b.x + 5, b.y + 5, 0xFFFFFFFF, true);
            graphics.text(font, state.anchor.name(), b.x + 5, b.y + 5 + font.lineHeight, 0xFFCCCCCC, true);
        }

        WidgetState state = AgalarHackClient.HUD_LAYOUT.get(selected);
        String status = state.anchor.name() + "  offset " + state.offsetX + ", " + state.offsetY;
        graphics.centeredText(font, status, width / 2, height - 42, 0xFFCCCCCC);
    }

    private void updateBounds() {
        bounds.clear();
        for (String id : WIDGETS) {
            bounds.put(id, boundsFor(id));
        }
    }

    private Bounds boundsFor(String id) {
        int w;
        int h;
        switch (id) {
            case "branding" -> {
                w = Math.max(110, font.width(AgalarHackClient.NAME + " " + AgalarHackClient.VERSION) + 10);
                h = 34;
            }
            case "modules" -> {
                w = 125;
                h = 70;
            }
            case "info" -> {
                w = 130;
                h = 48;
            }
            case "target" -> {
                w = 155;
                h = 82;
            }
            default -> {
                w = 100;
                h = 40;
            }
        }
        int x = AgalarHackClient.HUD_LAYOUT.resolveX(id, width, w);
        int y = AgalarHackClient.HUD_LAYOUT.resolveY(id, height, h);
        return new Bounds(x, y, w, h);
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

    private record Bounds(int x, int y, int width, int height) {
        boolean contains(double px, double py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }
}
