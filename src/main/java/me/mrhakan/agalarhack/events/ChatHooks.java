package me.mrhakan.agalarhack.events;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import me.mrhakan.agalarhack.AgalarHackClient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Bridge between the chat mixin and the client.
 *
 * <p>Fabric 26.2 offers {@code ALLOW_CHAT} and {@code CHAT} but nothing that rewrites a line before
 * it is drawn, so a timestamp needs the one place every added message passes through. This keeps the
 * mixin trivial: it holds no state beyond the formatter, resolves the module defensively, and never
 * lets a failure escape into Minecraft's chat handling — a client that cannot draw chat is far worse
 * than one without timestamps.
 */
public final class ChatHooks {
    private ChatHooks() { }

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter WITH_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * @param message the line about to be added
     * @return the line to add, unchanged when the feature is off or anything goes wrong
     */
    public static Component decorate(Component message) {
        if (message == null) return null;
        try {
            var manager = AgalarHackClient.moduleManager;
            if (manager == null) return message;
            var module = manager.getModule("BetterChat");
            if (!(module instanceof me.mrhakan.agalarhack.module.misc.BetterChat chat) || !chat.isToggled()) {
                return message;
            }
            if (!chat.getBooleanSetting("timestamps", true)) return message;
            String stamp = LocalTime.now().format(chat.getBooleanSetting("seconds", false) ? WITH_SECONDS : SHORT);
            MutableComponent prefix = Component.literal("[" + stamp + "] ")
                    .withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY));
            return prefix.append(message);
        } catch (RuntimeException failure) {
            // Chat that fails to draw is far worse than chat without a timestamp.
            AgalarHackClient.LOGGER.error("Chat decoration failed; showing the message unchanged", failure);
            return message;
        }
    }
}
