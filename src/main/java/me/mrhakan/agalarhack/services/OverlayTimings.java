package me.mrhakan.agalarhack.services;

import java.util.List;

/**
 * Per-overlay render cost, the frame-based counterpart of {@link ModuleTimings}.
 *
 * <p>Module tick timings miss render work entirely: an ESP's tick only publishes targets, and the
 * boxes are written every frame. World overlays run twice a frame (labels while the frame is
 * collected, lines in a deferred pass afterwards), so one frame's cost for an overlay is the sum of
 * both, and it is only known once the next frame starts. {@link #beginFrame} therefore closes the
 * previous frame, records one sample per overlay that drew in it, and opens the next.
 *
 * <p>The two passes are also kept apart, as "{@code <overlay> labels}" and "{@code <overlay> lines}"
 * in {@link #slowestParts}, because an optimisation needs to know which half it is cutting: ESP draws
 * both a box and a name tag per target, and the total alone cannot say which one is the cost.
 *
 * <p>Recording is self-expiring exactly like module timings: nothing is measured unless a consumer
 * has called {@link #requestRecording()} within the last {@value #IDLE_FRAMES} frames, and outside
 * that the per-frame cost is one {@link ModuleTimings#beginTick()} call. Kept free of Minecraft types
 * so the accumulation and expiry rules are unit tested directly.
 */
public final class OverlayTimings {
    /** Two seconds at 60 frames per second. */
    public static final int WINDOW = 120;
    /** Recording stops this many frames after the last request; three seconds at 60 frames per second. */
    public static final int IDLE_FRAMES = 180;

    private final ModuleTimings frames = new ModuleTimings(WINDOW, IDLE_FRAMES);
    private final ModuleTimings parts = new ModuleTimings(WINDOW, IDLE_FRAMES);
    private Frame open;

    /** One frame's running cost, one slot per overlay in draw order. */
    public static final class Frame {
        private final String[] names;
        private final long[] labelNanos;
        private final long[] lineNanos;

        private Frame(int slots) {
            names = new String[slots];
            labelNanos = new long[slots];
            lineNanos = new long[slots];
        }

        /** Marks a slot as drawn this frame, under the name it is reported by. */
        public void drawn(int slot, String overlay) {
            names[slot] = overlay;
        }

        /** Time spent submitting the overlay's labels while the frame is collected. */
        public void addLabels(int slot, long elapsedNanos) {
            if (elapsedNanos > 0) labelNanos[slot] += elapsedNanos;
        }

        /** Time spent writing the overlay's lines in the deferred pass. */
        public void addLines(int slot, long elapsedNanos) {
            if (elapsedNanos > 0) lineNanos[slot] += elapsedNanos;
        }
    }

    /** Called by anything that wants figures; keeps recording alive for {@value #IDLE_FRAMES} frames. */
    public void requestRecording() {
        frames.requestRecording();
        parts.requestRecording();
    }

    public boolean isRecording() {
        return frames.isRecording();
    }

    /**
     * Closes the frame before this one and opens the next.
     *
     * <p>Each overlay that drew in the closed frame contributes one sample: its labels and lines
     * together. An overlay that did not draw is dropped by the next frame, for the same reason
     * {@link ModuleTimings#beginTick()} drops idle modules.
     *
     * @param slots how many overlays this frame can draw
     * @return the accumulator for this frame, or {@code null} while nothing is asking for figures
     */
    public Frame beginFrame(int slots) {
        frames.beginTick();
        parts.beginTick();
        Frame closed = open;
        open = null;
        if (!frames.isRecording()) return null;
        if (closed != null) {
            for (int slot = 0; slot < closed.names.length; slot++) {
                String name = closed.names[slot];
                if (name == null) continue;
                long labels = closed.labelNanos[slot], lines = closed.lineNanos[slot];
                frames.record(name, labels + lines);
                if (labels > 0) parts.record(name + " labels", labels);
                if (lines > 0) parts.record(name + " lines", lines);
            }
        }
        open = new Frame(Math.max(0, slots));
        return open;
    }

    /** @return the costliest overlays by average per frame, worst first; {@code ticks} counts frames */
    public List<ModuleTimings.Entry> slowest(int limit) {
        return frames.slowest(limit);
    }

    /**
     * The same frames split by pass: "{@code ESP labels}" and "{@code ESP lines}" rather than "{@code ESP}".
     * A pass that took no measurable time in a frame is left out of that frame.
     */
    public List<ModuleTimings.Entry> slowestParts(int limit) {
        return parts.slowest(limit);
    }

    /** Average cost of every overlay that drew, per frame. */
    public double totalAverageMicros() {
        return frames.totalAverageMicros();
    }

    public void clear() {
        frames.clear();
        parts.clear();
        open = null;
    }
}
