package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameModeTrackerTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static GameModeTracker.PlayerState state(String name, String mode) {
        return new GameModeTracker.PlayerState(name, mode);
    }

    @Test
    void firstSnapshotOnlyPrimesTheTracker() {
        var tracker = new GameModeTracker();
        assertTrue(tracker.update(Map.of(FIRST, state("Alice", "survival"))).isEmpty());
        assertTrue(tracker.primed());
        assertEquals(1, tracker.size());
    }

    @Test
    void changedModeProducesOneChangeWithBothModes() {
        var tracker = new GameModeTracker();
        tracker.update(Map.of(FIRST, state("Alice", "SURVIVAL")));
        List<GameModeTracker.Change> changes =
                tracker.update(Map.of(FIRST, state("Alice", "creative")));
        assertEquals(1, changes.size());
        var change = changes.get(0);
        assertEquals(FIRST, change.id());
        assertEquals("Alice", change.name());
        assertEquals("survival", change.previousMode());
        assertEquals("creative", change.currentMode());
    }

    @Test
    void unchangedModeDoesNotRepeat() {
        var tracker = new GameModeTracker();
        tracker.update(Map.of(FIRST, state("Alice", "survival")));
        assertTrue(tracker.update(Map.of(FIRST, state("Alice", "survival"))).isEmpty());
        assertTrue(tracker.update(Map.of(FIRST, state("Alice", "survival"))).isEmpty());
    }

    @Test
    void removedPlayersAreForgotten() {
        var tracker = new GameModeTracker();
        tracker.update(Map.of(FIRST, state("Alice", "survival")));
        assertTrue(tracker.update(Map.of()).isEmpty());
        assertTrue(tracker.update(Map.of(FIRST, state("Alice", "creative"))).isEmpty());
    }

    @Test
    void nullSnapshotsAndEntriesAreSafe() {
        var tracker = new GameModeTracker();
        assertTrue(tracker.update(null).isEmpty());
        Map<UUID, GameModeTracker.PlayerState> current = new LinkedHashMap<>();
        current.put(null, state("ignored", "creative"));
        current.put(SECOND, null);
        assertTrue(tracker.update(current).isEmpty());
        assertTrue(tracker.primed());
        assertEquals(0, tracker.size());
    }

    @Test
    void capacityRemainsBounded() {
        var tracker = new GameModeTracker(2);
        Map<UUID, GameModeTracker.PlayerState> current = new LinkedHashMap<>();
        for (int index = 0; index < 8; index++) {
            current.put(new UUID(0, index + 1L), state("P" + index, "survival"));
        }
        tracker.update(current);
        assertEquals(2, tracker.size());
    }

    @Test
    void readdingAfterRemovalEstablishesABaseline() {
        var tracker = new GameModeTracker();
        tracker.update(Map.of(FIRST, state("Alice", "survival")));
        tracker.update(Map.of());
        assertTrue(tracker.update(Map.of(FIRST, state("Alice", "creative"))).isEmpty());
        assertTrue(tracker.update(Map.of(FIRST, state("Alice", "adventure"))).size() == 1);
    }

    @Test
    void playerStateNormalizationIsStable() {
        var state = new GameModeTracker.PlayerState("  Alice  ", " CREATIVE ");
        assertEquals("Alice", state.name());
        assertEquals("creative", state.mode());
        assertEquals("Unknown", new GameModeTracker.PlayerState(" ", "").name());
        assertEquals("unknown", new GameModeTracker.PlayerState(null, null).mode());
    }
}
