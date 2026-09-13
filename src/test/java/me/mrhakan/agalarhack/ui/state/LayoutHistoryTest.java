package me.mrhakan.agalarhack.ui.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LayoutHistoryTest {

    /** Stands in for a widget state: mutable, which is the whole reason copies are needed. */
    private static final class Box {
        int x;
        Box(int x) { this.x = x; }
        Box copy() { return new Box(x); }
        @Override public boolean equals(Object other) { return other instanceof Box box && box.x == x; }
        @Override public int hashCode() { return x; }
        @Override public String toString() { return "Box(" + x + ")"; }
    }

    private static LayoutHistory<Box> history() {
        return new LayoutHistory<>(Box::copy);
    }

    private static Map<String, Box> layout(int x) {
        Map<String, Box> map = new LinkedHashMap<>();
        map.put("fps", new Box(x));
        return map;
    }

    @Test
    void nothingToUndoAtTheStart() {
        LayoutHistory<Box> history = history();
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
        assertNull(history.undo(layout(0)));
        assertNull(history.redo(layout(0)));
    }

    @Test
    void undoRestoresWhatWasThereBeforeTheChange() {
        LayoutHistory<Box> history = history();
        Map<String, Box> live = layout(10);
        history.record(live);
        live.get("fps").x = 99;
        assertEquals(layout(10), history.undo(live));
    }

    @Test
    void aSnapshotIsACopyNotALiveReference() {
        LayoutHistory<Box> history = history();
        Map<String, Box> live = layout(10);
        history.record(live);
        // Mutating the same objects afterwards must not reach into the snapshot.
        live.get("fps").x = 99;
        live.put("added", new Box(1));
        Map<String, Box> restored = history.undo(live);
        assertEquals(1, restored.size());
        assertEquals(10, restored.get("fps").x);
    }

    @Test
    void redoComesBackToTheStateUndoLeft() {
        LayoutHistory<Box> history = history();
        Map<String, Box> live = layout(10);
        history.record(live);
        Map<String, Box> changed = layout(99);
        Map<String, Box> undone = history.undo(changed);
        assertEquals(layout(10), undone);
        assertTrue(history.canRedo());
        assertEquals(layout(99), history.redo(undone));
    }

    @Test
    void aNewChangeMakesTheRedoneAwayFutureUnreachable() {
        LayoutHistory<Box> history = history();
        history.record(layout(1));
        history.undo(layout(2));
        assertTrue(history.canRedo());
        history.record(layout(3));
        assertFalse(history.canRedo(), "every editor drops the future on a new edit");
    }

    @Test
    void severalStepsUndoInReverseOrder() {
        LayoutHistory<Box> history = history();
        history.record(layout(1));
        history.record(layout(2));
        history.record(layout(3));
        assertEquals(layout(3), history.undo(layout(4)));
        assertEquals(layout(2), history.undo(layout(3)));
        assertEquals(layout(1), history.undo(layout(2)));
        assertFalse(history.canUndo());
    }

    @Test
    void historyIsBoundedAndDropsTheOldest() {
        LayoutHistory<Box> history = history();
        for (int index = 0; index < LayoutHistory.MAX_ENTRIES * 2; index++) history.record(layout(index));
        assertEquals(LayoutHistory.MAX_ENTRIES, history.undoDepth());
        // The most recent entries are the ones that survived.
        assertEquals(layout(LayoutHistory.MAX_ENTRIES * 2 - 1), history.undo(layout(999)));
    }

    @Test
    void redoStackIsBoundedTheSameWay() {
        LayoutHistory<Box> history = history();
        for (int index = 0; index < LayoutHistory.MAX_ENTRIES * 2; index++) history.record(layout(index));
        Map<String, Box> live = layout(999);
        for (int index = 0; index < LayoutHistory.MAX_ENTRIES; index++) live = history.undo(live);
        assertEquals(LayoutHistory.MAX_ENTRIES, history.redoDepth());
    }

    @Test
    void recordingNothingIsIgnoredRatherThanStoringAnEmptyStep() {
        LayoutHistory<Box> history = history();
        history.record(null);
        assertFalse(history.canUndo());
    }

    @Test
    void clearDropsBothDirections() {
        LayoutHistory<Box> history = history();
        history.record(layout(1));
        history.undo(layout(2));
        history.clear();
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
    }

    @Test
    void aNullValueInTheLayoutDoesNotThrow() {
        LayoutHistory<Box> history = history();
        Map<String, Box> live = new LinkedHashMap<>();
        live.put("missing", null);
        history.record(live);
        assertNull(history.undo(live).get("missing"));
    }
}
