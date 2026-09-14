package me.mrhakan.agalarhack.services.scanning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScanBudgetsTest {

    @Test
    void balancedIsExactlyWhatTheClientUsedBefore() {
        assertEquals(12_000, ScanBudgets.BALANCED.blocks());
        assertEquals(64, ScanBudgets.BALANCED.chunkLookups());
        assertEquals(4096, ScanBudgets.BALANCED.entities());
    }

    @Test
    void anUnknownProfileBehavesAsBalancedRatherThanStopping() {
        // "LOW " included deliberately: input is not trimmed, and falling back to balanced is the
        // safe reading of a name that is not exactly one of the three.
        for (String profile : new String[] { null, "", "medium", "LOW ", "nonsense" }) {
            assertEquals(ScanBudgets.BALANCED, ScanBudgets.forProfile(profile), "profile was " + profile);
        }
    }

    @Test
    void profileNamesAreCaseInsensitive() {
        assertEquals(ScanBudgets.forProfile("low"), ScanBudgets.forProfile("LOW"));
        assertEquals(ScanBudgets.forProfile("high"), ScanBudgets.forProfile("High"));
    }

    @Test
    void theProfilesAreOrderedAndScaleTogether() {
        ScanBudgets low = ScanBudgets.forProfile("low");
        ScanBudgets balanced = ScanBudgets.forProfile("balanced");
        ScanBudgets high = ScanBudgets.forProfile("high");
        assertTrue(low.blocks() < balanced.blocks() && balanced.blocks() < high.blocks());
        assertTrue(low.chunkLookups() < balanced.chunkLookups() && balanced.chunkLookups() < high.chunkLookups(),
                "raising block probes without chunk lookups just moves where the scan stalls");
        assertTrue(low.entities() < balanced.entities() && balanced.entities() < high.entities());
    }

    @Test
    void everyProfileLeavesRoomToMakeProgress() {
        for (String profile : new String[] { "low", "balanced", "high" }) {
            ScanBudgets budgets = ScanBudgets.forProfile(profile);
            assertTrue(budgets.blocks() >= 1 && budgets.chunkLookups() >= 1 && budgets.entities() >= 1, profile);
        }
    }

    @Test
    void aHandEditedConfigCannotStallTheTickOrZeroTheScanner() {
        ScanBudgets huge = new ScanBudgets(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(ScanBudgets.MAX, huge.blocks());
        assertEquals(ScanBudgets.MAX, huge.chunkLookups());
        assertEquals(ScanBudgets.MAX, huge.entities());

        ScanBudgets empty = new ScanBudgets(0, -10, Integer.MIN_VALUE);
        assertEquals(1, empty.blocks());
        assertEquals(1, empty.chunkLookups());
        assertEquals(1, empty.entities());
    }
}
