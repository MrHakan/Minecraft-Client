package me.mrhakan.agalarhack.module.misc;

import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.events.EventBus;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
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
    private boolean laggingReported;

    public ServerInfo() {
        super("ServerInfo", Category.MISC, "Ping history and an estimated server tick rate, both clearly labelled as client-side");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("pingInterval", 20, 5, 200, "Ticks between ping samples");
        addNumberSetting("lagThreshold", 15.0, 1.0, 19.5, "Warn when the estimated tick rate falls below this");
        addBooleanSetting("lagWarning", false, "Notify when the estimate drops below the threshold");
    }

    public ServerTickEstimate tickEstimate() { return ticks; }
    public RollingSamples pingSamples() { return pings; }

    @Override
    public void onEnable() {
        reset();
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
        pingCooldown = 0;
        laggingReported = false;
    }

    private void onServerTime(ClientEvents.ServerTimeUpdated event) {
        long advanced = lastGameTime == Long.MIN_VALUE ? 0 : event.gameTime() - lastGameTime;
        lastGameTime = event.gameTime();
        if (advanced <= 0) return;
        ticks.update(System.currentTimeMillis(), advanced);
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (pingCooldown-- <= 0) {
            pingCooldown = (int) Math.round(getNumberSetting("pingInterval", 20));
            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null && info.getLatency() > 0) pings.add(info.getLatency());
        }
        if (ticks.hasEstimate()) {
            setDisplayName(String.format(java.util.Locale.ROOT, "ServerInfo [~%.1f tps]", ticks.average()));
            warnIfLagging();
        } else {
            setDisplayName(null);
        }
    }

    /** Fires once per lag episode rather than every tick, and re-arms when the rate recovers. */
    private void warnIfLagging() {
        if (!getBooleanSetting("lagWarning", false)) return;
        double threshold = getNumberSetting("lagThreshold", 15.0);
        double average = ticks.average();
        if (average < threshold && !laggingReported) {
            laggingReported = true;
            service(NotificationService.class).publish(NotificationService.Type.WARNING,
                    String.format(java.util.Locale.ROOT, "Server looks slow: ~%.1f tps (client estimate)", average));
        } else if (average >= threshold + 1.0) {
            laggingReported = false;
        }
    }
}
