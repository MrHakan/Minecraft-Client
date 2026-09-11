package me.mrhakan.agalarhack.services;

import java.util.Arrays;

/**
 * A fixed-size ring of recent numeric samples.
 *
 * <p>Used by the ping graph and the TPS estimate. Kept deliberately dumb: it stores what it is told
 * and reports simple statistics. Nothing here infers anything about the server, because the client
 * cannot; the callers are responsible for labelling their figures as estimates.
 */
public final class RollingSamples {
    public static final int MAX_CAPACITY = 1024;

    private final double[] values;
    private int count;
    private int next;

    public RollingSamples(int capacity) {
        this.values = new double[Math.max(1, Math.min(MAX_CAPACITY, capacity))];
    }

    /** Non-finite samples are ignored rather than poisoning every statistic that follows. */
    public void add(double value) {
        if (!Double.isFinite(value)) return;
        values[next] = value;
        next = (next + 1) % values.length;
        if (count < values.length) count++;
    }

    public int size() { return count; }

    public int capacity() { return values.length; }

    public boolean isEmpty() { return count == 0; }

    public void clear() { count = 0; next = 0; Arrays.fill(values, 0); }

    /** Samples oldest first. */
    public double[] snapshot() {
        double[] result = new double[count];
        int start = count < values.length ? 0 : next;
        for (int i = 0; i < count; i++) result[i] = values[(start + i) % values.length];
        return result;
    }

    public double latest() {
        return count == 0 ? 0 : values[(next - 1 + values.length) % values.length];
    }

    public double average() {
        if (count == 0) return 0;
        double total = 0;
        for (double value : snapshot()) total += value;
        return total / count;
    }

    public double minimum() {
        if (count == 0) return 0;
        double best = Double.MAX_VALUE;
        for (double value : snapshot()) best = Math.min(best, value);
        return best;
    }

    public double maximum() {
        if (count == 0) return 0;
        double best = -Double.MAX_VALUE;
        for (double value : snapshot()) best = Math.max(best, value);
        return best;
    }
}
