package me.mrhakan.agalarhack.ui;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.managers.HudLayoutManager.WidgetState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag-and-drop HUD editor with grid snapping and overlap diagnostics. */
public class HudEditorScreen extends Screen {
    private List<String> widgets(){return me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.ui.hud.HudRegistry.class).ids();}
    private static final int GRID_SIZE = 10;
    private static final int SNAP_THRESHOLD = 6;

    private final Screen parent;
    private String selected;
    private String dragging;
    private double grabX;
    private double grabY;
    private boolean gridVisible = true;
    private boolean snapping = true;
    private final Map<String, Bounds> bounds = new LinkedHashMap<>();

    public HudEditorScreen(Screen parent) {
        this(parent, "branding");
    }

    private HudEditorScreen(Screen parent, String selected) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
        this.selected = widgets().contains(selected) ? selected : "branding";
    }

    @Override
    public void init() {
        super.init();
        int bottom = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Anchor"), button ->
                AgalarHackClient.HUD_LAYOUT.cycleAnchor(selected))
                .bounds(8, bottom, 54, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Visible"), button ->
                AgalarHackClient.HUD_LAYOUT.toggleVisible(selected))
                .bounds(66, bottom, 54, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Grid"), button -> gridVisible = !gridVisible)
                .bounds(124, bottom, 44, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Snap"), button -> snapping = !snapping)
                .bounds(172, bottom, 44, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), button ->
                AgalarHackClient.HUD_LAYOUT.reset(selected))
                .bounds(220, bottom, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("All"), button ->
                AgalarHackClient.HUD_LAYOUT.resetAll())
                .bounds(274, bottom, 38, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(width - 58, bottom, 50, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xE010141C);
        if (!gridVisible) {
            return;
        }
        int limit = Math.max(0, height - 34);
        for (int x = 0; x < width; x += GRID_SIZE) {
            graphics.fill(x, 0, x + 1, limit, x % (GRID_SIZE * 5) == 0 ? 0x303E526B : 0x182C394A);
        }
        for (int y = 0; y < limit; y += GRID_SIZE) {
            graphics.fill(0, y, width, y + 1, y % (GRID_SIZE * 5) == 0 ? 0x303E526B : 0x182C394A);
        }
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
        for (int i = widgets().size() - 1; i >= 0; i--) {
            String id = widgets().get(i);
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
        Point snapped = snapPosition(dragging,
                (int) Math.round(event.x() - grabX),
                (int) Math.round(event.y() - grabY),
                b.width, b.height);
        AgalarHackClient.HUD_LAYOUT.moveTo(dragging, snapped.x, snapped.y,
                width, height, b.width, b.height, false, false);
        updateBounds();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null && event.button() == 0) {
            Bounds b = boundsFor(dragging);
            Point snapped = snapPosition(dragging,
                    (int) Math.round(event.x() - grabX),
                    (int) Math.round(event.y() - grabY),
                    b.width, b.height);
            AgalarHackClient.HUD_LAYOUT.moveTo(dragging, snapped.x, snapped.y,
                    width, height, b.width, b.height, true, true);
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
        Set<String> overlaps = overlappingWidgets();

        graphics.centeredText(font, "HUD EDITOR", width / 2, 7, 0xFFFFFFFF);
        graphics.centeredText(font,
                "Drag • grid " + (gridVisible ? "ON" : "OFF") + " • snap " + (snapping ? "ON" : "OFF")
                        + " • selected " + title(selected),
                width / 2, 20, 0xFF9FB1C7);

        for (String id : widgets()) {
            Bounds b = bounds.get(id);
            WidgetState state = AgalarHackClient.HUD_LAYOUT.get(id);
            boolean active = id.equals(selected);
            boolean collision = overlaps.contains(id);
            int fill = active ? 0x90425E7A : 0x70303A48;
            if (!state.visible) {
                fill = active ? 0x704B3D50 : 0x50252B34;
            }
            int border = collision ? 0xFFFF5E6C : active ? 0xFF74B9FF : 0xFF617185;
            graphics.fill(b.x, b.y, b.x + b.width, b.y + b.height, fill);
            graphics.outline(b.x, b.y, b.width, b.height, border);
            graphics.text(font, title(id) + (state.visible ? "" : " [hidden]"), b.x + 5, b.y + 5, 0xFFFFFFFF, true);
            graphics.text(font, state.anchor.name(), b.x + 5, b.y + 5 + font.lineHeight, 0xFFB8C5D6, true);
            if (collision) {
                graphics.text(font, "OVERLAP", b.x + 5, b.y + 5 + font.lineHeight * 2, 0xFFFF8A94, true);
            }
        }

        WidgetState state = AgalarHackClient.HUD_LAYOUT.get(selected);
        String status = state.anchor.name() + "  offset " + state.offsetX + ", " + state.offsetY;
        if (!overlaps.isEmpty()) {
            status += "  • overlap detected: " + String.join(", ", overlaps);
        }
        graphics.centeredText(font, status, width / 2, height - 42,
                overlaps.isEmpty() ? 0xFFB8C5D6 : 0xFFFF8A94);
    }

    private Point snapPosition(String id, int x, int y, int contentWidth, int contentHeight) {
        int maxX = Math.max(0, width - contentWidth);
        int maxY = Math.max(0, height - 34 - contentHeight);
        int snappedX = Math.max(0, Math.min(maxX, x));
        int snappedY = Math.max(0, Math.min(maxY, y));
        if (!snapping) {
            return new Point(snappedX, snappedY);
        }

        snappedX = Math.max(0, Math.min(maxX, Math.round(snappedX / (float) GRID_SIZE) * GRID_SIZE));
        snappedY = Math.max(0, Math.min(maxY, Math.round(snappedY / (float) GRID_SIZE) * GRID_SIZE));
        updateBounds();

        int bestX = snappedX;
        int bestXDistance = SNAP_THRESHOLD + 1;
        int bestY = snappedY;
        int bestYDistance = SNAP_THRESHOLD + 1;
        int[] ownX = {0, contentWidth / 2, contentWidth};
        int[] ownY = {0, contentHeight / 2, contentHeight};

        for (Map.Entry<String, Bounds> entry : bounds.entrySet()) {
            if (entry.getKey().equals(id)) {
                continue;
            }
            Bounds other = entry.getValue();
            int[] targetsX = {other.x, other.x + other.width / 2, other.x + other.width};
            int[] targetsY = {other.y, other.y + other.height / 2, other.y + other.height};
            for (int ownOffset : ownX) {
                for (int target : targetsX) {
                    int candidate = target - ownOffset;
                    int distance = Math.abs(candidate - snappedX);
                    if (distance <= SNAP_THRESHOLD && distance < bestXDistance) {
                        bestXDistance = distance;
                        bestX = candidate;
                    }
                }
            }
            for (int ownOffset : ownY) {
                for (int target : targetsY) {
                    int candidate = target - ownOffset;
                    int distance = Math.abs(candidate - snappedY);
                    if (distance <= SNAP_THRESHOLD && distance < bestYDistance) {
                        bestYDistance = distance;
                        bestY = candidate;
                    }
                }
            }
        }
        return new Point(Math.max(0, Math.min(maxX, bestX)), Math.max(0, Math.min(maxY, bestY)));
    }

    private Set<String> overlappingWidgets() {
        Set<String> overlaps = new LinkedHashSet<>();
        for (int i = 0; i < widgets().size(); i++) {
            Bounds a = bounds.get(widgets().get(i));
            for (int j = i + 1; j < widgets().size(); j++) {
                Bounds b = bounds.get(widgets().get(j));
                if (a != null && b != null && a.intersects(b)) {
                    overlaps.add(widgets().get(i));
                    overlaps.add(widgets().get(j));
                }
            }
        }
        return overlaps;
    }

    private void updateBounds() {
        bounds.clear();
        for (String id : widgets()) {
            bounds.put(id, boundsFor(id));
        }
    }

    private Bounds boundsFor(String id) {
        var component=me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.ui.hud.HudRegistry.class).get(id);
        int w=Math.max(70,Math.min(width,component.width().getAsInt()));
        int h=Math.max(30,Math.min(height-34,component.height().getAsInt()));
        int x = AgalarHackClient.HUD_LAYOUT.resolveX(id, width, w);
        int y = AgalarHackClient.HUD_LAYOUT.resolveY(id, height, h);
        return new Bounds(x, y, w, h);
    }

    private String title(String id) {
        var component=me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.ui.hud.HudRegistry.class).get(id);
        return component==null?id:component.title();
    }

    private record Point(int x, int y) {
    }

    private record Bounds(int x, int y, int width, int height) {
        boolean contains(double px, double py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }

        boolean intersects(Bounds other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
    }
}
