package me.mrhakan.agalarhack.services;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Bounded, distance-sampled player trail with allocation-free steady-state maintenance. */
public final class BreadcrumbTrail {
    public record Point(double x, double y, double z, long time) { }
    public static final int MAX_POINTS = 4096;

    private final Deque<Point> points = new ArrayDeque<>();
    private int capacity;
    private double minimumDistanceSquared;

    public BreadcrumbTrail(int capacity, double minimumDistance) { configure(capacity, minimumDistance); }

    public void configure(int capacity, double minimumDistance) {
        int nextCapacity = Math.max(2, Math.min(MAX_POINTS, capacity));
        double distance = Double.isFinite(minimumDistance) ? Math.max(0.05, minimumDistance) : 1.0;
        double nextDistanceSquared = distance * distance;
        if (this.capacity == nextCapacity && Double.compare(this.minimumDistanceSquared, nextDistanceSquared) == 0) return;
        this.capacity = nextCapacity;
        this.minimumDistanceSquared = nextDistanceSquared;
        trim();
    }

    public boolean sample(double x, double y, double z, long time) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        Point last = points.peekLast();
        if (last != null) {
            double dx = x - last.x(), dy = y - last.y(), dz = z - last.z();
            if (dx * dx + dy * dy + dz * dz < minimumDistanceSquared) return false;
        }
        points.addLast(new Point(x, y, z, time));
        trim();
        return true;
    }

    public void expire(long now, long maximumAge) {
        if (maximumAge <= 0) return;
        while (!points.isEmpty() && now - points.peekFirst().time() > maximumAge) points.removeFirst();
    }

    public List<Point> snapshot() { return List.copyOf(new ArrayList<>(points)); }
    public int size() { return points.size(); }
    public void clear() { points.clear(); }
    private void trim() { while (points.size() > capacity) points.removeFirst(); }
}
