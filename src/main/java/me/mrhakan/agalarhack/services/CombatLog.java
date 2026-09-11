package me.mrhakan.agalarhack.services;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A bounded record of who the client recently fought.
 *
 * <p>Only what the client itself did or saw: who was targeted, when, and the health it observed at
 * the time. The client is not told how much damage it dealt, so nothing here reports damage - a
 * figure derived from health differences would be wrong the moment a server heals, absorbs or
 * cancels a hit.
 *
 * <p>Free of Minecraft types so the ordering, deduplication and expiry rules are tested directly.
 */
public final class CombatLog {
    public record Entry(String name, long firstSeen, long lastSeen, int hits, float lastKnownHealth) { }

    public static final int MAX_ENTRIES = 32;

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final int capacity;

    public CombatLog(int capacity) {
        this.capacity = Math.max(1, Math.min(MAX_ENTRIES, capacity));
    }

    /**
     * Records an interaction with a target.
     *
     * <p>Repeated interactions with the same target update that entry and move it to the front
     * rather than filling the log with one name.
     */
    public void record(String name, long now, float observedHealth, boolean hit) {
        if (name == null || name.isBlank()) return;
        Entry existing = null;
        for (Entry entry : entries) {
            if (entry.name().equals(name)) { existing = entry; break; }
        }
        if (existing != null) {
            entries.remove(existing);
            entries.addFirst(new Entry(name, existing.firstSeen(), now,
                    existing.hits() + (hit ? 1 : 0), observedHealth));
        } else {
            entries.addFirst(new Entry(name, now, now, hit ? 1 : 0, observedHealth));
        }
        while (entries.size() > capacity) entries.removeLast();
    }

    /** Drops entries not touched within the window; a non-positive window keeps everything. */
    public void expire(long now, long window) {
        if (window <= 0) return;
        entries.removeIf(entry -> now - entry.lastSeen() > window);
    }

    /** Most recent first. */
    public List<Entry> recent() { return List.copyOf(new ArrayList<>(entries)); }

    public int size() { return entries.size(); }

    public void clear() { entries.clear(); }

    /** Seconds since the last interaction with anyone, or -1 when the log is empty. */
    public double secondsSinceCombat(long now) {
        Entry newest = entries.peekFirst();
        return newest == null ? -1 : Math.max(0, (now - newest.lastSeen()) / 1000.0);
    }
}
