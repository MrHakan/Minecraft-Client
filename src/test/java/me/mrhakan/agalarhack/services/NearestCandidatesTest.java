package me.mrhakan.agalarhack.services;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NearestCandidatesTest {
    @Test void capsResultsAndRetainsNearestWithStableTies() {
        var candidates = new NearestCandidates<String>(2);
        candidates.add("far", 100, 1);
        candidates.add("tie-high", 4, 20);
        candidates.add("near", 1, 5);
        candidates.add("tie-low", 4, 10);
        assertEquals(List.of("near", "tie-low"), candidates.snapshot());
        assertThrows(UnsupportedOperationException.class, () -> candidates.snapshot().clear());
    }
    @Test void rejectsInvalidScoresAndCapacity() {
        var candidates = new NearestCandidates<String>(1);
        candidates.add("nan", Double.NaN, 0);
        candidates.add("negative", -1, 1);
        candidates.add("infinite", Double.POSITIVE_INFINITY, 2);
        assertTrue(candidates.snapshot().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new NearestCandidates<>(513));
    }
}
