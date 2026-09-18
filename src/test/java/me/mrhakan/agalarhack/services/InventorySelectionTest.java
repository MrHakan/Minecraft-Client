package me.mrhakan.agalarhack.services;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InventorySelectionTest {
    @Test void boundedStableSelectionRejectsInvalidScores() {
        double[] scores = {Double.NaN, 5, 5, Double.POSITIVE_INFINITY};
        assertEquals(1, InventorySelection.best(4, 0, i -> scores[i]));
        assertEquals(-1, InventorySelection.best(0, 0, i -> 100));
        assertEquals(35, InventorySelection.best(10000, -1, i -> i));
    }
}
