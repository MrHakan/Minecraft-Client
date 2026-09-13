package me.mrhakan.agalarhack.ui.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RowAnimationsTest {
    private static final double ROW = 10;
    private static final double SLIDE = 20;

    private static List<String> ids(List<RowAnimations.Row> rows) {
        return rows.stream().map(RowAnimations.Row::id).toList();
    }

    private static RowAnimations.Row row(List<RowAnimations.Row> rows, String id) {
        return rows.stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void aNewRowStartsAsideAndTransparent() {
        var animations = new RowAnimations();
        var rows = animations.update(List.of("A"), ROW, SLIDE, 0.5, true);
        assertEquals(0.5, row(rows, "A").alpha(), 1e-9);
        assertEquals(SLIDE * 0.5, row(rows, "A").offsetX(), 1e-9);
    }

    @Test
    void aSettledRowIsFullyOpaqueAndNotOffset() {
        var animations = new RowAnimations();
        for (int frame = 0; frame < 60; frame++) animations.update(List.of("A"), ROW, SLIDE, 0.5, true);
        var rows = animations.update(List.of("A"), ROW, SLIDE, 0.5, true);
        assertEquals(1.0, row(rows, "A").alpha(), 1e-9);
        assertEquals(0.0, row(rows, "A").offsetX(), 1e-9);
    }

    @Test
    void aNewRowAppearsAtItsOwnPositionRatherThanSlidingDownTheList() {
        var animations = new RowAnimations();
        // Sliding in from wherever the list used to end would look like the wrong row moved.
        var rows = animations.update(List.of("A", "B", "C"), ROW, SLIDE, 0.5, true);
        assertEquals(0.0, row(rows, "A").y(), 1e-9);
        assertEquals(ROW, row(rows, "B").y(), 1e-9);
        assertEquals(ROW * 2, row(rows, "C").y(), 1e-9);
    }

    @Test
    void aRemovedRowKeepsBeingDrawnWhileItFades() {
        var animations = new RowAnimations();
        for (int frame = 0; frame < 20; frame++) animations.update(List.of("A", "B"), ROW, SLIDE, 0.5, true);
        var rows = animations.update(List.of("A"), ROW, SLIDE, 0.5, true);
        assertTrue(ids(rows).contains("B"), "it should fade, not vanish");
        assertTrue(row(rows, "B").alpha() < 1.0);
    }

    @Test
    void aRemovedRowIsEventuallyDropped() {
        var animations = new RowAnimations();
        animations.update(List.of("A", "B"), ROW, SLIDE, 0.5, true);
        for (int frame = 0; frame < 60; frame++) animations.update(List.of("A"), ROW, SLIDE, 0.5, true);
        assertEquals(List.of("A"), ids(animations.update(List.of("A"), ROW, SLIDE, 0.5, true)));
        assertEquals(1, animations.tracked(), "a faded row must not linger in the map forever");
    }

    @Test
    void rowsBelowASlideUpRatherThanJumping() {
        var animations = new RowAnimations();
        for (int frame = 0; frame < 30; frame++) animations.update(List.of("A", "B"), ROW, SLIDE, 0.5, true);
        var rows = animations.update(List.of("B"), ROW, SLIDE, 0.5, true);
        double y = row(rows, "B").y();
        assertTrue(y > 0 && y < ROW, "B should be on its way up, not already there: " + y);
    }

    @Test
    void reducedMotionSettlesImmediatelyWithNoOffset() {
        var animations = new RowAnimations();
        var rows = animations.update(List.of("A", "B"), ROW, SLIDE, 0.5, false);
        assertEquals(1.0, row(rows, "A").alpha(), 1e-9);
        assertEquals(0.0, row(rows, "A").offsetX(), 1e-9);
        assertEquals(ROW, row(rows, "B").y(), 1e-9);
        // And a removal is instant rather than a fade nobody asked for.
        assertEquals(List.of("A"), ids(animations.update(List.of("A"), ROW, SLIDE, 0.5, false)));
    }

    @Test
    void aStepOfOneSettlesImmediatelyToo() {
        var animations = new RowAnimations();
        var rows = animations.update(List.of("A"), ROW, SLIDE, 1.0, true);
        assertEquals(1.0, row(rows, "A").alpha(), 1e-9);
    }

    @Test
    void reorderingMovesRowsRatherThanReplacingThem() {
        var animations = new RowAnimations();
        for (int frame = 0; frame < 30; frame++) animations.update(List.of("A", "B"), ROW, SLIDE, 0.5, true);
        var rows = animations.update(List.of("B", "A"), ROW, SLIDE, 0.5, true);
        // Both stay fully opaque: they moved, they did not leave and come back.
        assertEquals(1.0, row(rows, "A").alpha(), 1e-9);
        assertEquals(1.0, row(rows, "B").alpha(), 1e-9);
        assertTrue(row(rows, "A").y() > 0 && row(rows, "A").y() < ROW, "A is on its way down");
    }

    @Test
    void trackingIsBounded() {
        var animations = new RowAnimations();
        List<String> many = new java.util.ArrayList<>();
        for (int index = 0; index < RowAnimations.MAX_TRACKED * 3; index++) many.add("row" + index);
        animations.update(many, ROW, SLIDE, 0.5, true);
        assertTrue(animations.tracked() <= RowAnimations.MAX_TRACKED, "was " + animations.tracked());
    }

    @Test
    void nullIdsAndNonsenseStepsAreSurvivable() {
        var animations = new RowAnimations();
        var rows = animations.update(java.util.Arrays.asList("A", null), ROW, SLIDE, Double.NaN, true);
        assertEquals(List.of("A"), ids(rows));
        assertFalse(Double.isNaN(row(rows, "A").alpha()));
    }

    @Test
    void clearForgetsEverything() {
        var animations = new RowAnimations();
        animations.update(List.of("A"), ROW, SLIDE, 0.5, true);
        animations.clear();
        assertEquals(0, animations.tracked());
        // A fresh row animates in again rather than resuming half way.
        assertEquals(0.5, row(animations.update(List.of("A"), ROW, SLIDE, 0.5, true), "A").alpha(), 1e-9);
    }
}
