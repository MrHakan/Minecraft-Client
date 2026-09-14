package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerAlertTrackerTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void firstAppearanceIsAccepted() {
        var tracker = new PlayerAlertTracker(8);
        assertTrue(tracker.accept(FIRST, 1000, 1500));
    }

    @Test
    void duplicateEntityAddedEventsAreSuppressed() {
        var tracker = new PlayerAlertTracker(8);
        assertTrue(tracker.accept(FIRST, 1000, 0));
        assertFalse(tracker.accept(FIRST, 1001, 0));
        assertEquals(1, tracker.activeSize());
    }

    @Test
    void removalAllowsAReappearanceAfterCooldown() {
        var tracker = new PlayerAlertTracker(8);
        assertTrue(tracker.accept(FIRST, 1000, 1500));
        assertTrue(tracker.remove(FIRST));
        assertTrue(tracker.accept(FIRST, 2500, 1500));
    }

    @Test
    void cooldownSuppressesAReappearanceButKeepsItActive() {
        var tracker = new PlayerAlertTracker(8);
        assertTrue(tracker.accept(FIRST, 1000, 1500));
        tracker.remove(FIRST);
        assertFalse(tracker.accept(FIRST, 1200, 1500));
        assertEquals(1, tracker.activeSize());
        tracker.remove(FIRST);
        assertTrue(tracker.accept(FIRST, 2500, 1500));
    }

    @Test
    void nullIdsAreIgnored() {
        var tracker = new PlayerAlertTracker(8);
        assertFalse(tracker.accept(null, 0, 0));
        assertFalse(tracker.remove(null));
        assertEquals(0, tracker.activeSize());
    }

    @Test
    void activeAndHistoryStateStayBounded() {
        var tracker = new PlayerAlertTracker(16);
        for (int index = 0; index < 64; index++) {
            UUID id = new UUID(0, index + 1L);
            assertTrue(tracker.accept(id, index, 0));
        }
        assertTrue(tracker.activeSize() <= 16);
        assertTrue(tracker.rememberedSize() <= 16);
    }

    @Test
    void clearStartsANewSession() {
        var tracker = new PlayerAlertTracker(8);
        assertTrue(tracker.accept(FIRST, 1000, 10000));
        tracker.clear();
        assertTrue(tracker.accept(FIRST, 1001, 10000));
    }

    @Test
    void negativeCooldownIsTreatedAsZero() {
        var tracker = new PlayerAlertTracker(8);
        assertTrue(tracker.accept(FIRST, 1000, -1));
        tracker.remove(FIRST);
        assertTrue(tracker.accept(FIRST, 1000, -1));
    }
}
