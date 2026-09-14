package me.mrhakan.agalarhack.services;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Retains only the nearest bounded candidates; deterministic IDs break distance ties. */
public final class NearestCandidates<T> {
    private record Candidate<T>(T value, double distanceSquared, int id) { }
    private final Comparator<Candidate<T>> order = Comparator.<Candidate<T>>comparingDouble(Candidate::distanceSquared)
            .thenComparingInt(Candidate::id);
    private final PriorityQueue<Candidate<T>> retained = new PriorityQueue<>(order.reversed());
    private final int maximum;
    public NearestCandidates(int maximum) {
        if (maximum < 1 || maximum > 512) throw new IllegalArgumentException("Invalid candidate limit");
        this.maximum = maximum;
    }
    public void add(T value, double distanceSquared, int id) {
        if (value == null || !Double.isFinite(distanceSquared) || distanceSquared < 0) return;
        var candidate = new Candidate<>(value, distanceSquared, id);
        if (retained.size() < maximum) retained.add(candidate);
        else if (order.compare(candidate, retained.peek()) < 0) { retained.remove(); retained.add(candidate); }
    }
    public List<T> snapshot() { return retained.stream().sorted(order).map(Candidate::value).toList(); }
}
