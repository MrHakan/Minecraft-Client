package me.mrhakan.agalarhack.module.misc;

import java.util.Locale;

import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.events.EventBus;
import me.mrhakan.agalarhack.managers.FriendManager;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.NotificationService;
import me.mrhakan.agalarhack.services.PlayerAlertTracker;
import net.minecraft.world.entity.player.Player;

/**
 * Reports other players entering or leaving this client's loaded entity set.
 *
 * <p>This is the useful, bounded part of the alarm idea: it uses the existing typed entity events and
 * notification HUD, never sends chat or sound packets, and does not claim to know about invisible or
 * unloaded players on the server.
 */
public final class PlayerAlerts extends Module {
    private final PlayerAlertTracker tracker = new PlayerAlertTracker();
    private EventBus.Subscription added;
    private EventBus.Subscription removed;

    public PlayerAlerts() {
        super("PlayerAlerts", Category.MISC,
                "Notifies when another player becomes visible or leaves the client render set").markExperimental();
    }

    @Override
    public void selfSettings() {
        addBooleanSetting("enter", true, "Notify when another player becomes visible to this client");
        addBooleanSetting("leave", false, "Notify when a tracked player leaves the client render set");
        addBooleanSetting("friendsOnly", false, "Only notify for players in the local friend list");
        addBooleanSetting("includeDistance", true, "Include the local client distance in the notice");
        addNumberSetting("cooldownMs", 1500, 0, 10000,
                "Minimum time before the same player can notify again");
    }

    @Override
    public void onEnable() {
        tracker.clear();
        closeSubscriptions();
        EventBus events = service(EventBus.class);
        added = events.subscribe(ClientEvents.EntityAdded.class, "player-alerts-add", 0, this::onEntityAdded);
        removed = events.subscribe(ClientEvents.EntityRemoved.class, "player-alerts-remove", 0, this::onEntityRemoved);
    }

    @Override
    public void onDisable() {
        closeSubscriptions();
        tracker.clear();
    }

    @Override
    public void onDisconnect() {
        onDisable();
    }

    private void onEntityAdded(ClientEvents.EntityAdded event) {
        if (!(event.entity() instanceof Player player)
                || event.level() != mc.level
                || mc.player == null
                || player == mc.player
                || !getBooleanSetting("enter", true)) {
            return;
        }

        String name = player.getName().getString();
        if (!tracker.accept(player.getUUID(), System.nanoTime() / 1_000_000L,
                Math.round(getNumberSetting("cooldownMs", 1500)))) {
            return;
        }
        if (!allowed(name)) {
            return;
        }

        String distance = "";
        if (getBooleanSetting("includeDistance", true)) {
            distance = String.format(Locale.ROOT, " (%.0fm)", mc.player.distanceTo(player));
        }
        service(NotificationService.class).publish(NotificationService.Type.INFO,
                "Player entered visual range: " + name + distance);
    }

    private void onEntityRemoved(ClientEvents.EntityRemoved event) {
        if (!(event.entity() instanceof Player player) || event.level() != mc.level
                || mc.player == null || player == mc.player) {
            return;
        }
        boolean tracked = tracker.remove(player.getUUID());
        if (!tracked || !getBooleanSetting("leave", false)) {
            return;
        }
        String name = player.getName().getString();
        if (!allowed(name)) {
            return;
        }

        String distance = "";
        if (getBooleanSetting("includeDistance", true)) {
            distance = String.format(Locale.ROOT, " (last seen %.0fm)", mc.player.distanceTo(player));
        }
        service(NotificationService.class).publish(NotificationService.Type.INFO,
                "Player left visual range: " + name + distance);
    }

    private boolean allowed(String name) {
        return !getBooleanSetting("friendsOnly", false)
                || service(FriendManager.class).isFriend(name);
    }

    private void closeSubscriptions() {
        if (added != null) {
            added.close();
            added = null;
        }
        if (removed != null) {
            removed.close();
            removed = null;
        }
    }
}
