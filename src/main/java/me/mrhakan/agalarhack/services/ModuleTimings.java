package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-module tick cost, measured only while something is asking to see it.
 *
 * <p>"The client feels slow" is not actionable, and with nearly fifty modules the only useful answer
 * names one. This keeps a short rolling window per module so the answer reflects what is happening
 * now rather than an average dragged down by the minutes the module spent idle.
 *
 * <p>Measurement is self-expiring on purpose. A consumer calls {@link #requestRecording()} each time
 * it wants figures; recording stops on its own once nothing has asked for {@value #IDLE_TICKS} ticks.
 * That keeps two {@code nanoTime} calls per module per tick out of the normal path without needing a
 * setting that can be left on, and without the HUD having to tell anyone it was closed.
 *
 * <p>Kept free of Minecraft types so the window, ranking and expiry rules are unit tested directly.
 */
public final class ModuleTimings {
    /** Roughly two seconds at 20 tps: long enough to smooth a spike, short enough to stay current. */
    public static final int WINDOW = 40;
    /** Recording stops this many ticks after the last request; three seconds of slack. */
    public static final int IDLE_TICKS = 60;
    /** Hard bound on tracked names, so a mis-keyed caller cannot grow this without limit. */
    public static final int MAX_MODULES = 128;

    private final Map<String, RollingSamples> samples = new LinkedHashMap<>();
    private final java.util.Set<String> seenThisTick = new java.util.HashSet<>();
    private int ticksSinceRequest = IDLE_TICKS + 1;

    /** @param module name, {@code averageMicros} the mean over the window, {@code peakMicros} its worst tick */
    public record Entry(String module, double averageMicros, double peakMicros, int ticks) { }

    /** Called by anything that wants figures; keeps recording alive for {@value #IDLE_TICKS} ticks. */
    public void requestRecording() {
        ticksSinceRequest = 0;
    }

    /**
     * Called once per client tick before the modules run.
     *
     * <p>Also drops any module that did not report last tick. A module that is switched off, or that
     * the manager skips because there is no world, costs nothing — leaving its last average on screen
     * would name an innocent module as the expensive one.
     */
    public void beginTick() {
        if (ticksSinceRequest <= IDLE_TICKS) ticksSinceRequest++;
        if (ticksSinceRequest > IDLE_TICKS) {
            if (!samples.isEmpty()) clear();
            return;
        }
        samples.keySet().retainAll(seenThisTick);
        seenThisTick.clear();
    }

    /**
     * True while figures are wanted. Callers check this rather than calling {@link #record} blindly,
     * so the {@code nanoTime} pair around each module is skipped too, not just the bookkeeping.
     */
    public boolean isRecording() {
        return ticksSinceRequest <= IDLE_TICKS;
    }

    public void record(String module, long nanos) {
        if (!isRecording() || module == null || nanos < 0) return;
        RollingSamples window = samples.get(module);
        if (window == null) {
            if (samples.size() >= MAX_MODULES) return;
            window = new RollingSamples(WINDOW);
            samples.put(module, window);
        }
        window.add(nanos / 1000.0);
        seenThisTick.add(module);
    }

    /** Drops a module's history immediately, rather than waiting for the next tick to notice. */
    public void forget(String module) {
        samples.remove(module);
        seenThisTick.remove(module);
    }

    public void clear() {
        samples.clear();
        seenThisTick.clear();
    }

    public int trackedModules() {
        return samples.size();
    }

    /**
     * @return the costliest modules by average, worst first, at most {@code limit} of them
     */
    public List<Entry> slowest(int limit) {
        List<Entry> entries = new ArrayList<>(samples.size());
        samples.forEach((module, window) -> {
            if (window.isEmpty()) return;
            entries.add(new Entry(module, window.average(), window.maximum(), window.size()));
        });
        entries.sort(Comparator.comparingDouble(Entry::averageMicros).reversed()
                .thenComparing(Entry::module, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(entries.subList(0, Math.min(Math.max(0, limit), entries.size())));
    }

    /** Total average cost of every tracked module in one tick, which is the figure that matters. */
    public double totalAverageMicros() {
        double total = 0;
        for (RollingSamples window : samples.values()) total += window.average();
        return total;
    }
}
