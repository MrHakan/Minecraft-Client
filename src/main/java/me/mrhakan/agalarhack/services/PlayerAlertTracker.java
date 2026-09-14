package me.mrhakan.agalarhack.services;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.UUID;

/**
 * Bounded lifecycle state for player visibility notifications.
 *
 * <p>Entity-added callbacks can be repeated by a reload or replacement. An active UUID is therefore
 * accepted once until the matching removal, while a short alert history prevents a player who
 * leaves and immediately returns from producing a toast storm. The state is intentionally small and
 * client-local; it is not a player database.
 */
public final class PlayerAlertTracker {
    public static final int DEFAULT_CAPACITY = 256;
    public static final int MAX_CAPACITY = 2048;

    private final int capacity;
    private final LinkedHashSet<UUID> active = new LinkedHashSet<>();
    private final LinkedHashMap<UUID, Long> lastAlert =
            new LinkedHashMap<>(16, 0.75f, true);

    public PlayerAlertTracker() {
        this(DEFAULT_CAPACITY);
    }

    public PlayerAlertTracker(int capacity) {
        this.capacity = Math.max(1, Math.min(MAX_CAPACITY, capacity));
    }

    /**
     * Marks an entity active and reports whether an alert should be shown.
     *
     * <p>The active mark is recorded even when the cooldown suppresses the alert. That is what makes
     * duplicate add callbacks harmless rather than a way around the cooldown.
     */
    public boolean accept(UUID id, long nowMillis, long cooldownMillis) {
        if (id == null || !active.add(id)) {
            return false;
        }
        trimActive();

        Long previous = lastAlert.get(id);
        long cooldown = Math.max(0L, cooldownMillis);
        long elapsed = previous == null || nowMillis < previous ? Long.MAX_VALUE : nowMillis - previous;
        if (previous != null && elapsed < cooldown) {
            return false;
        }

        lastAlert.put(id, nowMillis);
        trimAlerts();
        return true;
    }

    /** Removes the active mark and reports whether it was present. */
    public boolean remove(UUID id) {
        return id != null && active.remove(id);
    }

    public int activeSize() {
        return active.size();
    }

    public int rememberedSize() {
        return lastAlert.size();
    }

    public void clear() {
        active.clear();
        lastAlert.clear();
    }

    private void trimActive() {
        while (active.size() > capacity) {
            Iterator<UUID> iterator = active.iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private void trimAlerts() {
        while (lastAlert.size() > capacity) {
            Iterator<UUID> iterator = lastAlert.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }
}
