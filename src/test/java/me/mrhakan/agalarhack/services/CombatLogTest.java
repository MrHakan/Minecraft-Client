package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatLogTest {
    @Test void theMostRecentTargetComesFirst() {
        var log = new CombatLog(8);
        log.record("A", 0, 20, true);
        log.record("B", 10, 18, true);
        assertEquals("B", log.recent().get(0).name());
        assertEquals("A", log.recent().get(1).name());
    }

    @Test void repeatedTargetsUpdateInPlaceRatherThanFillingTheLog() {
        var log = new CombatLog(8);
        log.record("A", 0, 20, true);
        log.record("A", 10, 15, true);
        log.record("A", 20, 10, false);
        assertEquals(1, log.size());
        var entry = log.recent().get(0);
        assertEquals(0, entry.firstSeen());
        assertEquals(20, entry.lastSeen());
        assertEquals(2, entry.hits(), "only actual hits are counted");
        assertEquals(10f, entry.lastKnownHealth());
    }

    @Test void aRepeatedTargetMovesBackToTheFront() {
        var log = new CombatLog(8);
        log.record("A", 0, 20, true);
        log.record("B", 5, 20, true);
        log.record("A", 10, 18, true);
        assertEquals("A", log.recent().get(0).name());
    }

    @Test void theOldestEntryIsDroppedWhenFull() {
        var log = new CombatLog(2);
        log.record("A", 0, 20, true);
        log.record("B", 1, 20, true);
        log.record("C", 2, 20, true);
        assertEquals(2, log.size());
        assertFalse(log.recent().stream().anyMatch(entry -> entry.name().equals("A")));
    }

    @Test void expiryDropsOnlyStaleEntriesAndOnlyWhenEnabled() {
        var log = new CombatLog(8);
        log.record("A", 0, 20, true);
        log.record("B", 1000, 20, true);
        log.expire(1500, 0);
        assertEquals(2, log.size(), "a non-positive window keeps everything");
        log.expire(1500, 800);
        assertEquals(1, log.size());
        assertEquals("B", log.recent().get(0).name());
    }

    @Test void timeSinceCombatReportsMinusOneWhenNothingHappened() {
        var log = new CombatLog(8);
        assertEquals(-1, log.secondsSinceCombat(1000));
        log.record("A", 1000, 20, true);
        assertEquals(2.0, log.secondsSinceCombat(3000), 1e-9);
    }

    @Test void blankNamesAreIgnored() {
        var log = new CombatLog(8);
        log.record(null, 0, 20, true);
        log.record("  ", 0, 20, true);
        assertEquals(0, log.size());
    }

    @Test void capacityIsBounded() {
        var log = new CombatLog(0);
        log.record("A", 0, 20, true);
        log.record("B", 1, 20, true);
        assertEquals(1, log.size());
        assertDoesNotThrow(() -> new CombatLog(Integer.MAX_VALUE));
    }

    @Test void recentIsAnImmutableSnapshot() {
        var log = new CombatLog(8);
        log.record("A", 0, 20, true);
        var snapshot = log.recent();
        log.clear();
        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(null));
    }
}
