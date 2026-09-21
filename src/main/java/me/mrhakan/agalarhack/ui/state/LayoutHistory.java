package me.mrhakan.agalarhack.ui.state;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Undo and redo for the HUD editor.
 *
 * <p>Arranging a HUD is an experiment: you drag something, decide it was better before, and have no
 * way back except dragging it again by eye. Reset throws away the whole layout, which is not the same
 * thing at all.
 *
 * <p>Snapshots are whole-layout rather than per-widget deltas. A drag can move one widget, a snap can
 * move it and change its anchor, and "duplicate" adds one — expressing all of that as reversible
 * operations is a lot of machinery for a map of a few dozen small records, and a whole-map copy is
 * both simpler and impossible to get subtly wrong.
 *
 * <p>Generic over the value so this holds no Minecraft types and the stack behaviour is unit tested
 * directly; the copy function is supplied because the values are mutable and storing live references
 * would make every snapshot the current state.
 */
public final class LayoutHistory<T> {
    /** Bounded because each entry is a full copy; deep history of a HUD drag is not worth memory. */
    public static final int MAX_ENTRIES = 32;

    private final Deque<Map<String, T>> past = new ArrayDeque<>();
    private final Deque<Map<String, T>> future = new ArrayDeque<>();
    private final Function<T, T> copy;

    public LayoutHistory(Function<T, T> copy) {
        this.copy = copy;
    }

    /**
     * Records the state as it was <em>before</em> a change.
     *
     * <p>Call this immediately before mutating, not after: undo has to restore what was there, and a
     * snapshot taken afterwards restores the change you were trying to undo.
     */
    public void record(Map<String, T> current) {
        if (current == null) return;
        past.addLast(snapshot(current));
        while (past.size() > MAX_ENTRIES) past.removeFirst();
        // A new change makes any redone-away future unreachable, which is what every editor does.
        future.clear();
    }

    public boolean canUndo() {
        return !past.isEmpty();
    }

    public boolean canRedo() {
        return !future.isEmpty();
    }

    /**
     * @param current the live state, which is pushed onto the redo stack
     * @return the state to restore, or null when there is nothing to undo
     */
    public Map<String, T> undo(Map<String, T> current) {
        if (past.isEmpty()) return null;
        future.addLast(snapshot(current));
        while (future.size() > MAX_ENTRIES) future.removeFirst();
        return past.removeLast();
    }

    /** @return the state to restore, or null when there is nothing to redo */
    public Map<String, T> redo(Map<String, T> current) {
        if (future.isEmpty()) return null;
        past.addLast(snapshot(current));
        while (past.size() > MAX_ENTRIES) past.removeFirst();
        return future.removeLast();
    }

    public void clear() {
        past.clear();
        future.clear();
    }

    public int undoDepth() {
        return past.size();
    }

    public int redoDepth() {
        return future.size();
    }

    private Map<String, T> snapshot(Map<String, T> source) {
        Map<String, T> result = new LinkedHashMap<>();
        if (source == null) return result;
        source.forEach((key, value) -> result.put(key, value == null ? null : copy.apply(value)));
        return result;
    }
}
