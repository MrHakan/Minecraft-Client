package me.mrhakan.agalarhack.services;

import java.util.concurrent.atomic.LongAdder;

/**
 * How many packets actually crossed the wire, per second.
 *
 * <p>This is the one network figure the client genuinely knows. The tick estimate elsewhere is
 * inferred and labelled as such; a packet count is not inferred at all — the client either received
 * a packet or it did not — so it is the honest way to tell "the server went quiet" from "my
 * connection is fine and nothing is happening".
 *
 * <p>Counting happens on the netty thread and reading on the client thread, which is why the counters
 * are {@link LongAdder}s and the sampling is a separate step. Nothing here reaches back into a
 * service registry: {@code Connection.channelRead0} runs for every packet, so the hot path is two
 * volatile reads and, when counting is off, nothing else.
 *
 * <p>Counting is self-expiring for the same reason module timing is: a consumer asks each time it
 * wants figures and counting stops on its own, so the increment stays off the hot path unless
 * something is actually showing the number.
 */
public final class PacketRates {
    /** Counting stops this long after the last request; three seconds of slack at 20 tps. */
    public static final int IDLE_TICKS = 60;
    /** Seconds of history the rate is averaged over. */
    public static final int WINDOW_SECONDS = 5;

    /** The instance the network thread increments; null before startup and after shutdown. */
    private static volatile PacketRates active;

    private final LongAdder inbound = new LongAdder();
    private final LongAdder outbound = new LongAdder();
    private final RollingSamples inboundRates = new RollingSamples(WINDOW_SECONDS);
    private final RollingSamples outboundRates = new RollingSamples(WINDOW_SECONDS);

    private volatile boolean counting;
    private int ticksSinceRequest = IDLE_TICKS + 1;
    private long lastSampleMillis = Long.MIN_VALUE;
    private long lastInbound;
    private long lastOutbound;

    /** Publishes this instance to the network thread. Called once, from the composition root. */
    public void install() {
        active = this;
    }

    /** Called from the netty thread for every received packet. */
    public static void countInbound() {
        PacketRates rates = active;
        if (rates != null && rates.counting) rates.inbound.increment();
    }

    /** Called from the netty thread for every sent packet. */
    public static void countOutbound() {
        PacketRates rates = active;
        if (rates != null && rates.counting) rates.outbound.increment();
    }

    /** Called by anything showing the figures; keeps counting alive for {@value #IDLE_TICKS} ticks. */
    public void requestCounting() {
        ticksSinceRequest = 0;
        counting = true;
    }

    public boolean isCounting() {
        return counting;
    }

    /**
     * Called once per client tick.
     *
     * @param nowMillis a monotonic clock; rates are only recomputed once a second has passed
     */
    public void tick(long nowMillis) {
        if (ticksSinceRequest <= IDLE_TICKS) ticksSinceRequest++;
        if (ticksSinceRequest > IDLE_TICKS) {
            if (counting) reset();
            return;
        }
        if (lastSampleMillis == Long.MIN_VALUE) {
            lastSampleMillis = nowMillis;
            lastInbound = inbound.sum();
            lastOutbound = outbound.sum();
            return;
        }
        long elapsed = nowMillis - lastSampleMillis;
        if (elapsed < 1000) return;
        long in = inbound.sum();
        long out = outbound.sum();
        // Divided by the real elapsed time rather than assumed to be exactly a second: a stalled
        // client tick would otherwise report a rate several times higher than what arrived.
        inboundRates.add((in - lastInbound) * 1000.0 / elapsed);
        outboundRates.add((out - lastOutbound) * 1000.0 / elapsed);
        lastInbound = in;
        lastOutbound = out;
        lastSampleMillis = nowMillis;
    }

    public boolean hasSamples() {
        return !inboundRates.isEmpty();
    }

    public double inboundPerSecond() {
        return inboundRates.average();
    }

    public double outboundPerSecond() {
        return outboundRates.average();
    }

    /** A new connection counts from zero; carrying the previous server's rate across would be wrong. */
    public void reset() {
        counting = false;
        ticksSinceRequest = IDLE_TICKS + 1;
        inbound.reset();
        outbound.reset();
        inboundRates.clear();
        outboundRates.clear();
        lastSampleMillis = Long.MIN_VALUE;
        lastInbound = 0;
        lastOutbound = 0;
    }
}
