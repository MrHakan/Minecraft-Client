package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded comparison state for client-visible player-list game modes.
 *
 * <p>The server may expose only a partial list and a mode can arrive after the first tick. The first
 * complete snapshot is therefore a baseline, not a change. Removed players are forgotten so a later
 * rejoin establishes a new baseline instead of producing a false transition.
 */
public final class GameModeTracker {
    public static final int DEFAULT_CAPACITY = 256;
    public static final int MAX_CAPACITY = 2048;

    public record PlayerState(String name, String mode) {
        public PlayerState {
            name = name == null || name.isBlank() ? "Unknown" : name.trim();
            mode = mode == null || mode.isBlank()
                    ? "unknown"
                    : mode.trim().toLowerCase(Locale.ROOT);
        }
    }

    public record Change(UUID id, String name, String previousMode, String currentMode) { }

    private final int capacity;
    private final LinkedHashMap<UUID, PlayerState> observed = new LinkedHashMap<>();
    private boolean primed;

    public GameModeTracker() {
        this(DEFAULT_CAPACITY);
    }

    public GameModeTracker(int capacity) {
        this.capacity = Math.max(1, Math.min(MAX_CAPACITY, capacity));
    }

    /**
     * Compares a client-visible snapshot with the previous one.
     *
     * <p>Updates and removals are applied atomically from the caller's point of view: the returned
     * changes describe only the transition from the last accepted snapshot to this one.
     */
    public List<Change> update(Map<UUID, PlayerState> current) {
        LinkedHashMap<UUID, PlayerState> next = new LinkedHashMap<>();
        if (current != null) {
            for (var entry : current.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    next.put(entry.getKey(), entry.getValue());
                }
            }
        }

        if (!primed) {
            replace(next);
            primed = true;
            return List.of();
        }

        List<Change> changes = new ArrayList<>();
        for (var entry : next.entrySet()) {
            PlayerState previous = observed.get(entry.getKey());
            PlayerState now = entry.getValue();
            if (previous != null && !previous.mode().equals(now.mode())) {
                changes.add(new Change(entry.getKey(), now.name(), previous.mode(), now.mode()));
            }
        }
        replace(next);
        return List.copyOf(changes);
    }

    public boolean primed() {
        return primed;
    }

    public int size() {
        return observed.size();
    }

    public void reset() {
        observed.clear();
        primed = false;
    }

    private void replace(LinkedHashMap<UUID, PlayerState> next) {
        observed.clear();
        observed.putAll(next);
        while (observed.size() > capacity) {
            observed.remove(observed.keySet().iterator().next());
        }
    }
}
