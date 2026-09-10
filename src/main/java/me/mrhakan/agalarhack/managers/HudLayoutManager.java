package me.mrhakan.agalarhack.managers;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;

/** Persistent positions for the small set of first-party HUD widgets. */
public class HudLayoutManager {
    public enum Anchor {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        public Anchor next() {
            Anchor[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public static final class WidgetState {
        public Anchor anchor;
        public int offsetX;
        public int offsetY;
        public boolean visible;

        public WidgetState() {
            this(Anchor.TOP_LEFT, 2, 2, true);
        }

        public WidgetState(Anchor anchor, int offsetX, int offsetY, boolean visible) {
            this.anchor = anchor;
            this.offsetX = Math.max(0, offsetX);
            this.offsetY = Math.max(0, offsetY);
            this.visible = visible;
        }

        public WidgetState copy() {
            return new WidgetState(anchor, offsetX, offsetY, visible);
        }
    }

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("agalarhack-hud.json");
    private final Map<String, WidgetState> defaults = new LinkedHashMap<>();
    private final Map<String, WidgetState> widgets = new LinkedHashMap<>();

    public HudLayoutManager() {
        for(String id : new String[]{"branding","modules","info","target"}) defaults.put(id,defaultFor(id));
        resetDefaults();
    }
    public void registerDefault(String id,WidgetState state) {
        defaults.putIfAbsent(id,state.copy());widgets.putIfAbsent(id,state.copy());
    }

    public void load() {
        if (!Files.isRegularFile(path)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<String, WidgetState> loaded = gson.fromJson(reader, new TypeToken<Map<String, WidgetState>>(){}.getType());
            if (loaded != null) {
                for (Map.Entry<String, WidgetState> entry : loaded.entrySet()) {
                    WidgetState state = sanitize(entry.getValue());
                    if (state != null) {
                        widgets.put(entry.getKey(), state);
                    }
                }
            }
        } catch (Exception e) {
            me.mrhakan.agalarhack.AgalarHackClient.LOGGER.warn("[Agalar Hack] Failed to load HUD layout: " + e.getMessage());
        }
        ensureDefaults();
    }

    public void save() {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                gson.toJson(widgets, writer);
            }
        } catch (IOException e) {
            me.mrhakan.agalarhack.AgalarHackClient.LOGGER.warn("[Agalar Hack] Failed to save HUD layout: " + e.getMessage());
        }
    }

    public WidgetState get(String id) {
        ensureDefaults();
        return widgets.get(id);
    }

    public Map<String, WidgetState> snapshot() {
        Map<String, WidgetState> copy = new LinkedHashMap<>();
        widgets.forEach((key, value) -> copy.put(key, value.copy()));
        return copy;
    }

    public void applySnapshot(Map<String, WidgetState> snapshot) {
        resetDefaults();
        if (snapshot != null) {
            snapshot.forEach((key, value) -> {
                WidgetState state = sanitize(value);
                if (state != null) {
                    widgets.put(key, state);
                }
            });
        }
        ensureDefaults();
        save();
    }

    public void move(String id, int dx, int dy) {
        WidgetState state = get(id);
        state.offsetX = Math.max(0, state.offsetX + dx);
        state.offsetY = Math.max(0, state.offsetY + dy);
        save();
    }

    /**
     * Moves a widget by absolute screen coordinates. During a drag callers can
     * keep {@code persist=false}; the final mouse release re-anchors to the nearest
     * corner and persists once.
     */
    public void moveTo(String id, int x, int y, int screenWidth, int screenHeight,
            int contentWidth, int contentHeight, boolean autoAnchor, boolean persist) {
        WidgetState state = get(id);
        int clampedX = Math.max(0, Math.min(Math.max(0, screenWidth - contentWidth), x));
        int clampedY = Math.max(0, Math.min(Math.max(0, screenHeight - contentHeight), y));

        if (autoAnchor) {
            boolean right = clampedX + contentWidth / 2 >= screenWidth / 2;
            boolean bottom = clampedY + contentHeight / 2 >= screenHeight / 2;
            state.anchor = bottom
                    ? (right ? Anchor.BOTTOM_RIGHT : Anchor.BOTTOM_LEFT)
                    : (right ? Anchor.TOP_RIGHT : Anchor.TOP_LEFT);
        }

        state.offsetX = switch (state.anchor) {
            case TOP_RIGHT, BOTTOM_RIGHT -> Math.max(0, screenWidth - contentWidth - clampedX);
            default -> clampedX;
        };
        state.offsetY = switch (state.anchor) {
            case BOTTOM_LEFT, BOTTOM_RIGHT -> Math.max(0, screenHeight - contentHeight - clampedY);
            default -> clampedY;
        };

        if (persist) {
            save();
        }
    }

    public void cycleAnchor(String id) {
        WidgetState state = get(id);
        state.anchor = state.anchor.next();
        save();
    }

    public void toggleVisible(String id) {
        WidgetState state = get(id);
        state.visible = !state.visible;
        save();
    }

    public void reset(String id) {
        widgets.put(id, defaults.getOrDefault(id,defaultFor(id)).copy());
        save();
    }

    public void resetAll() {
        resetDefaults();
        save();
    }

    public int resolveX(String id, int screenWidth, int contentWidth) {
        WidgetState state = get(id);
        int x = switch (state.anchor) {
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - contentWidth - state.offsetX;
            default -> state.offsetX;
        };
        return Math.max(0, Math.min(Math.max(0, screenWidth - contentWidth), x));
    }

    public int resolveY(String id, int screenHeight, int contentHeight) {
        WidgetState state = get(id);
        int y = switch (state.anchor) {
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - contentHeight - state.offsetY;
            default -> state.offsetY;
        };
        return Math.max(0, Math.min(Math.max(0, screenHeight - contentHeight), y));
    }

    private void resetDefaults() {
        widgets.clear();
        defaults.forEach((id,state)->widgets.put(id,state.copy()));
    }

    private void ensureDefaults() {
        defaults.forEach((id,state)->widgets.putIfAbsent(id,state.copy()));
    }

    private WidgetState defaultFor(String id) {
        return switch (id) {
            case "modules" -> new WidgetState(Anchor.TOP_RIGHT, 2, 2, true);
            case "info" -> new WidgetState(Anchor.BOTTOM_LEFT, 2, 2, true);
            case "target" -> new WidgetState(Anchor.TOP_LEFT, 8, 54, true);
            default -> new WidgetState(Anchor.TOP_LEFT, 2, 2, true);
        };
    }

    private WidgetState sanitize(WidgetState state) {
        if (state == null) {
            return null;
        }
        if (state.anchor == null) {
            state.anchor = Anchor.TOP_LEFT;
        }
        state.offsetX = Math.max(0, Math.min(10000, state.offsetX));
        state.offsetY = Math.max(0, Math.min(10000, state.offsetY));
        return state;
    }
}
