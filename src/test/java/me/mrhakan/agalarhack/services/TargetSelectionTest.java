package me.mrhakan.agalarhack.services;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TargetSelectionTest {
    @Test void geometryClampsWallRangeAndWrapsFov() {
        assertTrue(TargetSelection.inRange(4, true, 3, 0, 359, 10));
        assertFalse(TargetSelection.inRange(4, false, 3, 0, 0, 360));
        assertFalse(TargetSelection.inRange(16, false, 3, 10, 0, 360));
        assertFalse(TargetSelection.inRange(Double.NaN, true, 3, 3, 0, 360));
    }
    @Test void prioritiesUseDistanceAndIdToBreakTies() {
        var a = new TargetSelection.Metrics(1, 4, 10, 20, 30, 40, 0, false);
        var b = new TargetSelection.Metrics(2, 9, 5, 0, 10, 15, 5, true);
        assertTrue(TargetSelection.comparator("closest").compare(a,b) < 0);
        assertTrue(TargetSelection.comparator("lowest_health").compare(a,b) > 0);
        assertTrue(TargetSelection.comparator("recent_attacker").compare(a,b) > 0);
        assertTrue(TargetSelection.comparator("hurt_time").compare(a,b) < 0);
    }
}
