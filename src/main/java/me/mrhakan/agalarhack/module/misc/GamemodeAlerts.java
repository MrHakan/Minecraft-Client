package me.mrhakan.agalarhack.module.misc;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.GameModeTracker;
import me.mrhakan.agalarhack.services.NotificationService;

/**
 * Reports game-mode changes visible in the client player list.
 *
 * <p>This clean-room notifier is intentionally narrower than packet-driven addon variants: it polls
 * the list the client already exposes, so it adds no mixin and never claims knowledge hidden from
 * the client. The first snapshot is a baseline and reconnects/world changes reset it.
 */
public final class GamemodeAlerts extends Module {
    private static final String DEFAULT_MODES = "survival,creative,adventure,spectator";

    private final GameModeTracker tracker = new GameModeTracker();
    private String parsedModes;
    private Set<String> modes = Set.of();

    public GamemodeAlerts() {
        super("GamemodeAlerts", Category.MISC,
                "Notifies when a visible player's client-reported game mode changes").markExperimental();
    }

    @Override
    public void selfSettings() {
        addBooleanSetting("self", true, "Notify when your own listed game mode changes");
        addBooleanSetting("others", true, "Notify when another listed player's game mode changes");
        settings.addSetting("modes", DEFAULT_MODES);
        addBooleanSetting("notifyUnknown", false, "Also report modes outside the configured list");
    }

    @Override
    public void onEnable() {
        tracker.reset();
        parsedModes = null;
        modes = Set.of();
    }

    @Override
    public void onDisable() {
        tracker.reset();
        parsedModes = null;
        modes = Set.of();
    }

    @Override
    public void onDisconnect() {
        onDisable();
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.getConnection() == null) {
            tracker.reset();
            return;
        }

        Map<UUID, GameModeTracker.PlayerState> current = new LinkedHashMap<>();
        for (var info : mc.getConnection().getOnlinePlayers()) {
            if (info == null || info.getProfile() == null || info.getGameMode() == null) {
                continue;
            }
            var profile = info.getProfile();
            UUID id = profile.id();
            if (id == null) {
                continue;
            }
            current.put(id, new GameModeTracker.PlayerState(profile.name(),
                    String.valueOf(info.getGameMode())));
        }

        UUID selfId = mc.player.getUUID();
        // Do not prime on a partial join snapshot that has not listed the local player yet.
        if (!current.containsKey(selfId)) {
            return;
        }

        Set<String> watched = watchedModes();
        for (GameModeTracker.Change change : tracker.update(current)) {
            boolean self = selfId.equals(change.id());
            if ((self && !getBooleanSetting("self", true))
                    || (!self && !getBooleanSetting("others", true))) {
                continue;
            }
            if (!getBooleanSetting("notifyUnknown", false)
                    && !watched.contains(change.currentMode())) {
                continue;
            }
            service(NotificationService.class).publish(NotificationService.Type.INFO,
                    change.name() + " changed gamemode to " + change.currentMode()
                            + " (client-visible)");
        }
    }

    private Set<String> watchedModes() {
        String raw = getStringSetting("modes", DEFAULT_MODES);
        if (!raw.equals(parsedModes)) {
            java.util.LinkedHashSet<String> parsed = new java.util.LinkedHashSet<>();
            for (String token : raw.split("[,\\n]")) {
                String mode = token.trim().toLowerCase(Locale.ROOT);
                if (!mode.isEmpty()) {
                    parsed.add(mode);
                }
            }
            modes = Set.copyOf(parsed);
            parsedModes = raw;
        }
        return modes;
    }
}
