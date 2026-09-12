package me.mrhakan.agalarhack.managers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import me.mrhakan.agalarhack.config.BoundedJsonFile;

import net.fabricmc.loader.api.FabricLoader;

/** Bounded persistence for dynamically registered HUD widgets and editor preferences. */
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
        public boolean locked;
        public int zOrder;

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
            WidgetState copy=new WidgetState(anchor,offsetX,offsetY,visible);copy.locked=locked;copy.zOrder=zOrder;return copy;
        }
    }

    public static class EditorOptions {
        public int schemaVersion=1;
        public int gridSize=10,snapStrength=6,safeMargin=4;
        public boolean gridVisible=true,snapping=true;
    }
    private EditorOptions editorOptions=new EditorOptions();
    public EditorOptions editorOptions(){return editorOptions;}
    public void saveEditorOptions() {
        try { editorFile.save(sanitizeOptions(editorOptions)); }
        catch (IOException failure) { me.mrhakan.agalarhack.AgalarHackClient.LOGGER.error("Could not save HUD editor options", failure); }
    }
    private void loadEditorOptions() {
        try { editorFile.load().ifPresent(loaded -> editorOptions = loaded); }
        catch (IOException failure) { me.mrhakan.agalarhack.AgalarHackClient.LOGGER.warn("HUD editor config preserved; saving disabled", failure); }
    }
    private static EditorOptions sanitizeOptions(EditorOptions options) {
        if (options == null || options.schemaVersion != 1) throw new IllegalArgumentException("Unsupported HUD editor schema");
        options.gridSize=Math.max(2,Math.min(64,options.gridSize));
        options.snapStrength=Math.max(0,Math.min(24,options.snapStrength));
        options.safeMargin=Math.max(0,Math.min(32,options.safeMargin));
        return options;
    }
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("agalarhack-hud.json");
    private final BoundedJsonFile<Map<String, WidgetState>> layoutFile = new BoundedJsonFile<>(path, 131072,
            raw -> validateSnapshot(gson.fromJson(raw, new TypeToken<Map<String, WidgetState>>(){}.getType())), gson::toJson);
    private final BoundedJsonFile<EditorOptions> editorFile = new BoundedJsonFile<>(path.resolveSibling("agalarhack-hud-editor.json"),
            8192, raw -> sanitizeOptions(gson.fromJson(raw, EditorOptions.class)), gson::toJson);
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
        loadEditorOptions();
        try {
            var loaded = layoutFile.load();
            if (loaded.isPresent()) {
                resetDefaults();
                widgets.putAll(loaded.get());
            } else save();
        } catch (IOException failure) {
            me.mrhakan.agalarhack.AgalarHackClient.LOGGER.warn("HUD layout preserved; saving disabled", failure);
        }
        ensureDefaults();
    }

    public void save() {
        try { layoutFile.save(validateSnapshot(widgets)); }
        catch (IOException | IllegalArgumentException failure) {
            me.mrhakan.agalarhack.AgalarHackClient.LOGGER.warn("Could not save HUD layout", failure);
        }
    }

    private Map<String, WidgetState> validateSnapshot(Map<String, WidgetState> source) {
        if (source == null || source.size() > 256) throw new IllegalArgumentException("Invalid HUD component count");
        Map<String, WidgetState> result = new LinkedHashMap<>();
        for (var entry : source.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[a-z0-9_.:-]{1,64}")) {
                throw new IllegalArgumentException("Invalid HUD component id");
            }
            WidgetState state = sanitize(entry.getValue() == null ? null : entry.getValue().copy());
            if (state != null) result.put(entry.getKey(), state);
        }
        return result;
    }

    /**
     * Never null.
     *
     * <p>A component registered without a declared default used to return null here, and the one
     * caller that copied the result turned that into a crash during client startup - before any
     * screen exists to report it. A widget nobody declared a placement for is a cosmetic problem;
     * refusing to start is not, so it gets the generic placement instead.
     *
     * <p>The state is stored rather than returned fresh each call, because callers mutate what they
     * are given - the HUD editor moves widgets by writing to it.
     */
    public WidgetState get(String id) {
        ensureDefaults();
        return widgets.computeIfAbsent(id, this::defaultFor);
    }

    public Map<String, WidgetState> snapshot() {
        Map<String, WidgetState> copy = new LinkedHashMap<>();
        widgets.forEach((key, value) -> copy.put(key, value.copy()));
        return copy;
    }

    public void applySnapshot(Map<String, WidgetState> snapshot) {
        Map<String, WidgetState> validated = snapshot == null ? Map.of() : validateSnapshot(snapshot);
        resetDefaults();
        widgets.putAll(validated);
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
     *
     * <p>The position is in the same logical space {@link #resolveX} returns, so the screen size
     * passed here is the physical one and is converted the same way. Anything else and a widget
     * dragged to the right edge in the editor would be stored against a different edge than the one
     * it is drawn against.
     */
    public void moveTo(String id, int x, int y, int physicalWidth, int physicalHeight,
            int contentWidth, int contentHeight, boolean autoAnchor, boolean persist) {
        int screenWidth = logicalWidth(physicalWidth);
        int screenHeight = logicalHeight(physicalHeight);
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

    /**
     * How large the HUD draws. Layout is done in the space the matrix is scaled into, so this has to
     * match what the render pass and the editor use; all three read it from {@link HudScale}.
     */
    private double scale = me.mrhakan.agalarhack.services.HudScale.DEFAULT;

    public void setScale(double value) { scale = me.mrhakan.agalarhack.services.HudScale.clamp(value); }

    public double scale() { return scale; }

    /** The screen size a widget is placed against: the real one divided by the scale. */
    public int logicalWidth(int screenWidth) {
        return me.mrhakan.agalarhack.services.HudScale.logical(screenWidth, scale);
    }

    public int logicalHeight(int screenHeight) {
        return me.mrhakan.agalarhack.services.HudScale.logical(screenHeight, scale);
    }

    public int resolveX(String id, int physicalWidth, int contentWidth) {
        int screenWidth = logicalWidth(physicalWidth);
        WidgetState state = get(id);
        int x = switch (state.anchor) {
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - contentWidth - state.offsetX;
            default -> state.offsetX;
        };
        return Math.max(0, Math.min(Math.max(0, screenWidth - contentWidth), x));
    }

    public int resolveY(String id, int physicalHeight, int contentHeight) {
        int screenHeight = logicalHeight(physicalHeight);
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
        state.zOrder=Math.max(-1000,Math.min(1000,state.zOrder));
        state.offsetX = Math.max(0, Math.min(10000, state.offsetX));
        state.offsetY = Math.max(0, Math.min(10000, state.offsetY));
        return state;
    }
}
