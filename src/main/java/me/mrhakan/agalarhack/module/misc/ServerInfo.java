package me.mrhakan.agalarhack.module.misc;

import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.events.EventBus;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.LatchingThreshold;
import me.mrhakan.agalarhack.services.NotificationService;
import me.mrhakan.agalarhack.services.RollingSamples;
import me.mrhakan.agalarhack.services.ServerTickEstimate;

/**
 * Client-visible server information: ping history and a tick-rate estimate.
 *
 * <p>The tick figure is an estimate derived from how far apart world-time updates arrive, and is
 * labelled as such everywhere it is shown. The client is never told the server's real tick rate, and
 * this module does not pretend otherwise.
 */
public class ServerInfo extends Module {
    private final ServerTickEstimate ticks = new ServerTickEstimate(40);
    private final RollingSamples pings = new RollingSamples(120);
    private EventBus.Subscription timeUpdates;
    private long lastGameTime = Long.MIN_VALUE;
    private int pingCooldown;
    /** Rebuilt on enable so a settings change takes effect without needing a reconnect. */
    private LatchingThreshold lagging;
    private LatchingThreshold pingSpiking;
    private LatchingThreshold frozen;
    private long lastTimeUpdateMillis = Long.MIN_VALUE;

    public ServerInfo() {
        super("ServerInfo", Category.MISC, "Ping history and an estimated server tick rate, both clearly labelled as client-side");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("pingInterval", 20, 5, 200, "Ticks between ping samples");
        addNumberSetting("lagThreshold", 15.0, 1.0, 19.5, "Warn when the estimated tick rate falls below this");
        addBooleanSetting("lagWarning", false, "Notify when the estimate drops below the threshold");
        addBooleanSetting("pingSpikeWarning", false, "Notify when your ping jumps well above its recent normal");
        addNumberSetting("pingSpikeFactor", 3.0, 1.5, 10.0, "How many times the recent median counts as a spike");
        addBooleanSetting("packetRates", false, "Count packets in and out per second while shown");
        addBooleanSetting("frozenWarning", false, "Notify when the server stops sending world time updates");
        addNumberSetting("frozenSeconds", 5.0, 2.0, 60.0, "Seconds without an update before saying so");
    }

    public ServerTickEstimate tickEstimate() { return ticks; }
    public RollingSamples pingSamples() { return pings; }

    /**
     * Packets per second, in and out.
     *
     * <p>The only network figure here that is not an estimate: the client either received a packet or
     * it did not. Counting is asked for on each call and stops on its own, so it costs nothing while
     * nobody is looking at it.
     *
     * @return a formatted line, or null when the setting is off or no second has elapsed yet
     */
    public String packetRateLine() {
        if (!getBooleanSetting("packetRates", false)) return null;
        var rates = service(me.mrhakan.agalarhack.services.PacketRates.class);
        rates.requestCounting();
        if (!rates.hasSamples()) return "Packets ...";
        return String.format(java.util.Locale.ROOT, "Packets %.0f in / %.0f out per s",
                rates.inboundPerSecond(), rates.outboundPerSecond());
    }

    /** Client-observable only: how long since the last world-time packet, not a claim about the server. */
    public double secondsSinceServerUpdate() {
        if (lastTimeUpdateMillis == Long.MIN_VALUE) return 0;
        return Math.max(0, System.currentTimeMillis() - lastTimeUpdateMillis) / 1000.0;
    }

    @Override
    public void onEnable() {
        reset();
        rebuildThresholds();
        if (timeUpdates != null) timeUpdates.close();
        timeUpdates = service(EventBus.class).subscribe(ClientEvents.ServerTimeUpdated.class,
                "server-info-time", 0, this::onServerTime);
    }

    @Override
    public void onDisable() {
        if (timeUpdates != null) { timeUpdates.close(); timeUpdates = null; }
        reset();
        setDisplayName(null);
    }

    @Override public void onDisconnect() { onDisable(); }

    /** A new world means a new server clock; carrying samples across would be nonsense. */
    @Override
    public void onWorldChanged(boolean ready) {
        reset();
        super.onWorldChanged(ready);
    }

    private void reset() {
        ticks.reset();
        pings.clear();
        lastGameTime = Long.MIN_VALUE;
        lastTimeUpdateMillis = Long.MIN_VALUE;
        pingCooldown = 0;
        if (lagging != null) lagging.reset();
        if (pingSpiking != null) pingSpiking.reset();
        if (frozen != null) frozen.reset();
    }

    /**
     * The release margins are what stop a value hovering at its threshold producing a wall of
     * identical warnings: one tick of the estimate, half a spike, and a full second of silence.
     */
    private void rebuildThresholds() {
        lagging = new LatchingThreshold(LatchingThreshold.Direction.BELOW,
                getNumberSetting("lagThreshold", 15.0), 1.0);
        double factor = getNumberSetting("pingSpikeFactor", 3.0);
        pingSpiking = new LatchingThreshold(LatchingThreshold.Direction.ABOVE, factor, factor / 2.0);
        frozen = new LatchingThreshold(LatchingThreshold.Direction.ABOVE,
                getNumberSetting("frozenSeconds", 5.0), 1.0);
    }

    private void onServerTime(ClientEvents.ServerTimeUpdated event) {
        long advanced = lastGameTime == Long.MIN_VALUE ? 0 : event.gameTime() - lastGameTime;
        lastGameTime = event.gameTime();
        if (advanced <= 0) return;
        lastTimeUpdateMillis = System.currentTimeMillis();
        ticks.update(lastTimeUpdateMillis, advanced);
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (lagging == null) rebuildThresholds();
        if (pingCooldown-- <= 0) {
            pingCooldown = (int) Math.round(getNumberSetting("pingInterval", 20));
            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null && info.getLatency() > 0) {
                warnIfPingSpiked(info.getLatency());
                pings.add(info.getLatency());
            }
        }
        warnIfFrozen();
        if (ticks.hasEstimate()) {
            setDisplayName(String.format(java.util.Locale.ROOT, "ServerInfo [~%.1f tps]", ticks.average()));
            warnIfLagging();
        } else {
            setDisplayName(null);
        }
    }

    /** Fires once per lag episode rather than every tick, and re-arms when the rate recovers. */
    private void warnIfLagging() {
        if (!getBooleanSetting("lagWarning", false)) { lagging.reset(); return; }
        double average = ticks.average();
        if (lagging.update(average)) {
            service(NotificationService.class).publish(NotificationService.Type.WARNING,
                    String.format(java.util.Locale.ROOT, "Server looks slow: ~%.1f tps (client estimate)", average));
        }
    }

    /**
     * Compares the new sample against the median of the previous ones, before it joins them.
     *
     * <p>The median rather than the average because one 2000 ms sample drags an average far enough to
     * raise its own threshold and hide the next spike. Comparing before adding matters for the same
     * reason: a spike should not be allowed to move the baseline it is being judged against.
     *
     * <p>This says your connection hiccuped, which the client can see. It says nothing about why.
     */
    private void warnIfPingSpiked(int latency) {
        if (!getBooleanSetting("pingSpikeWarning", false)) { pingSpiking.reset(); return; }
        // Too few samples to have a normal yet; anything would be a guess.
        if (pings.size() < 8) return;
        double baseline = pings.median();
        if (baseline <= 0) return;
        if (pingSpiking.update(latency / baseline)) {
            service(NotificationService.class).publish(NotificationService.Type.WARNING,
                    String.format(java.util.Locale.ROOT, "Ping spike: %d ms against a recent %.0f ms", latency, baseline));
        }
    }

    /**
     * Reports that nothing has arrived, which is the only half of this the client actually knows.
     *
     * <p>Deliberately worded as silence rather than as a frozen server: from here a stalled server, a
     * dropped connection and a suspended laptop look identical, and picking one would be inventing
     * the cause.
     */
    private void warnIfFrozen() {
        if (!getBooleanSetting("frozenWarning", false) || lastTimeUpdateMillis == Long.MIN_VALUE) {
            frozen.reset();
            return;
        }
        double silent = secondsSinceServerUpdate();
        if (frozen.update(silent)) {
            service(NotificationService.class).publish(NotificationService.Type.WARNING,
                    String.format(java.util.Locale.ROOT, "No server updates for %.0fs", silent));
        }
    }
}
