package me.mrhakan.agalarhack.services;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counts totem activations the client actually observed, per player.
 *
 * <p>The client only ever sees the activation event, never a server-side tally, so this counts what
 * it saw and nothing more. Counts reset on death and after a period without seeing the player, which
 * is the honest approximation: a player who left and came back is not still on five totems.
 *
 * <p>Bounded and free of Minecraft types so the counting and reset rules are tested directly.
 */
public final class TotemPopCounts {
    public static final int DEFAULT_CAPACITY = 128;

    private record Tally(int count, long lastSeen) { }

    private final int capacity;
    private final LinkedHashMap<String, Tally> counts;
    private long timeout;

    public TotemPopCounts(int capacity, long timeoutMillis) {
        this.capacity = Math.max(1, Math.min(1024, capacity));
        this.timeout = Math.max(0, timeoutMillis);
        this.counts = new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Tally> eldest) {
                return size() > TotemPopCounts.this.capacity;
            }
        };
    }

    public void setTimeout(long timeoutMillis) { this.timeout = Math.max(0, timeoutMillis); }

    /**
     * Records one activation.
     *
     * @return the running count for that player, starting at 1
     */
    public int pop(String player, long now) {
        if (player == null || player.isBlank()) return 0;
        expire(now);
        Tally existing = counts.get(player);
        int next = existing == null ? 1 : existing.count() + 1;
        counts.put(player, new Tally(next, now));
        return next;
    }

    /** Current count, or 0 if never seen or already expired. */
    public int count(String player, long now) {
        if (player == null) return 0;
        expire(now);
        Tally entry = counts.get(player);
        return entry == null ? 0 : entry.count();
    }

    /** Called when a player dies: the tally starts again from zero. */
    public void reset(String player) {
        if (player != null) counts.remove(player);
    }

    /** Drops players not seen within the timeout; a zero timeout keeps everything. */
    public void expire(long now) {
        if (timeout <= 0) return;
        counts.entrySet().removeIf(entry -> now - entry.getValue().lastSeen() > timeout);
    }

    public int tracked() { return counts.size(); }

    public void clear() { counts.clear(); }
}
