package me.mrhakan.agalarhack.ui.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Slide and fade for a list whose contents change while it is on screen.
 *
 * <p>Toggling a module makes its row appear or vanish instantly, and every row below it jump. The
 * jump is the problem: it draws the eye to the wrong place and, with several modules bound to nearby
 * keys, makes the list hard to read at exactly the moment you are checking what you just turned on.
 *
 * <p>The measured rectangle deliberately does <em>not</em> animate. The module list HUD promises the
 * HUD editor the same rectangle it draws in, and a height that changes mid-animation would make the
 * editor anchor jitter. So the box stays the settled size and only the rows move inside it: a row on
 * its way out fades and slides aside while the ones below slide up into its place.
 *
 * <p>Free of Minecraft types so the easing, the ordering and the eviction are unit tested directly.
 */
public final class RowAnimations {
    /** Bounded so a pathological toggle loop cannot grow the tracking map without limit. */
    public static final int MAX_TRACKED = 128;
    /** Below this the row is dropped; any lower and it lingers invisibly for several more frames. */
    private static final double GONE = 0.02;

    /**
     * @param id the row's identity
     * @param offsetX how far aside the row should be drawn, in pixels; 0 once settled
     * @param y the row's vertical position in pixels from the top of the list
     * @param alpha 0..1
     */
    public record Row(String id, double offsetX, double y, double alpha) { }

    private static final class State {
        double progress;
        double y;
        boolean present;
        boolean placed;
    }

    private final Map<String, State> rows = new LinkedHashMap<>();

    /**
     * Advances one frame and returns what to draw.
     *
     * @param present the ids currently in the list, in display order
     * @param rowHeight pixel height of one row
     * @param slide how far a row starts from its resting place, in pixels
     * @param step 0..1 easing step for this frame; 1 settles immediately
     * @param animate false disables motion entirely, for reduced-motion themes
     * @return rows to draw, including ones still fading out
     */
    public List<Row> update(List<String> present, double rowHeight, double slide, double step, boolean animate) {
        double eased = clamp01(Double.isFinite(step) ? step : 1);
        rows.values().forEach(state -> state.present = false);

        for (int index = 0; index < present.size(); index++) {
            String id = present.get(index);
            if (id == null) continue;
            State state = rows.get(id);
            if (state == null) {
                if (rows.size() >= MAX_TRACKED) continue;
                state = new State();
                rows.put(id, state);
            }
            state.present = true;
            double targetY = index * rowHeight;
            if (!state.placed) {
                // A row appearing for the first time starts at its own position rather than sliding
                // down from wherever the list used to end, which would look like the wrong row moved.
                state.y = targetY;
                state.placed = true;
                if (!animate) state.progress = 1;
            }
            state.progress = approach(state.progress, 1, eased, animate);
            state.y = approach(state.y, targetY, eased, animate);
        }

        List<Row> result = new ArrayList<>(rows.size());
        var iterator = rows.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            State state = entry.getValue();
            if (!state.present) {
                state.progress = approach(state.progress, 0, eased, animate);
                if (state.progress <= GONE) { iterator.remove(); continue; }
            }
            result.add(new Row(entry.getKey(), (1 - state.progress) * slide, state.y, clamp01(state.progress)));
        }
        return List.copyOf(result);
    }

    /** Drops every row, so re-opening a screen does not replay the last session's animation. */
    public void clear() {
        rows.clear();
    }

    public int tracked() {
        return rows.size();
    }

    /** Eases toward the goal, snapping once the remaining gap stops being visible. */
    static double approach(double current, double goal, double step, boolean animate) {
        if (!animate || step >= 1 || !Double.isFinite(current)) return goal;
        double next = current + (goal - current) * step;
        return Math.abs(goal - next) < 0.001 ? goal : next;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) return 0;
        return Math.max(0, Math.min(1, value));
    }
}
