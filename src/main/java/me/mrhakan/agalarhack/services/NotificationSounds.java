package me.mrhakan.agalarhack.services;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Plays a short UI cue for notifications.
 *
 * <p>Kept out of {@link NotificationService} so that service stays free of Minecraft types and
 * fully unit testable. Volume and which severities are audible are decided by the caller, so the
 * Notifications module owns the settings and this class owns only the Minecraft call.
 */
public final class NotificationSounds {
    private NotificationSounds() { }

    /** Distinct pitches rather than distinct sounds, so severity is audible without new assets. */
    public static float pitchFor(NotificationService.Type type) {
        return switch (type) {
            case ERROR -> 0.7f;
            case WARNING -> 0.9f;
            case INFO -> 1.2f;
            case SUCCESS -> 1.5f;
        };
    }

    /** True when a notice of this severity should be audible at the configured minimum. */
    public static boolean audible(NotificationService.Type type, String minimum) {
        int required = switch (minimum == null ? "all" : minimum.toLowerCase(java.util.Locale.ROOT)) {
            case "errors" -> 3;
            case "warnings" -> 2;
            default -> 0;
        };
        int severity = switch (type) {
            case ERROR -> 3;
            case WARNING -> 2;
            case SUCCESS -> 1;
            case INFO -> 0;
        };
        return severity >= required;
    }

    public static void play(NotificationService.Type type, float volume) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getSoundManager() == null || volume <= 0) return;
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(),
                pitchFor(type), Math.min(1.0f, volume)));
    }
}
