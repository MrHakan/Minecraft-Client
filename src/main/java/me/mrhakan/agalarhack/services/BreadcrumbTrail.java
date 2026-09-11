package me.mrhakan.agalarhack.services;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A bounded record of where the player has been.
 *
 * <p>Sampling is distance-based rather than per tick: standing still adds nothing, so the trail
 * length reflects ground covered instead of time spent. The deque is hard-capped and drops the
 * oldest point when full, which is what keeps a long session from growing without limit.
 *
 * <p>Free of Minecraft types so the sampling and eviction rules are unit tested.
 */
public final class BreadcrumbTrail {
    public record Point(double x, double y, double z, long time) { }

    public static final int MAX_POINTS = 4096;

    private final Deque<Point> points = new ArrayDeque<>();
    private int capacity;
    private double minimumDistanceSquared;

    public BreadcrumbTrail(int capacity, double minimumDistance) {
        configure(capacity, minimumDistance);
    }

    public void configure(int capacity, double minimumDistance) {
        this.capacity = Math.max(2, Math.min(MAX_POINTS, capacity));
        double distance = Double.isFinite(minimumDistance) ? Math.max(0.05, minimumDistance) : 1.0;
        this.minimumDistanceSquared = distance * distance;
        trim();
    }

    /**
     * Records a position if it is far enough from the last one.
     *
     * @return true when a point was added
     */
    public boolean sample(double x, double y, double z, long time) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        Point last = points.peekLast();
        if (last != null) {
            double dx = x - last.x();
            double dy = y - last.y();
            double dz = z - last.z();
            if (dx * dx + dy * dy + dz * dz < minimumDistanceSquared) return false;
        }
        points.addLast(new Point(x, y, z, time));
        trim();
        return true;
    }

    /** Drops points older than the given age; a non-positive duration keeps everything. */
    public void expire(long now, long maximumAge) {
        if (maximumAge <= 0) return;
        while (!points.isEmpty() && now - points.peekFirst().time() > maximumAge) {
            points.removeFirst();
        }
    }

    public List<Point> snapshot() { return List.copyOf(new ArrayList<>(points)); }

    public int size() { return points.size(); }

    public void clear() { points.clear(); }

    private void trim() {
        while (points.size() > capacity) points.removeFirst();
    }
}
