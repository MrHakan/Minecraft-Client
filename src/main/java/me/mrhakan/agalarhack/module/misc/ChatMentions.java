package me.mrhakan.agalarhack.module.misc;

import java.util.Set;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.ChatMatcher;
import me.mrhakan.agalarhack.services.NotificationService;
import me.mrhakan.agalarhack.services.NotificationSounds;

/**
 * Notifies when your name, a friend's name, or a configured keyword appears in chat.
 *
 * <p>Observation only: messages are never modified or hidden by this module. Fabric 26.2 has no
 * client hook for rewriting a chat line, so adding timestamps or inline highlighting would need a
 * mixin into chat rendering; that is deliberately left out rather than done fragilely.
 */
public class ChatMentions extends Module {
    private String parsedKeywords;
    private Set<String> keywords = Set.of();
    private long lastNotified;

    public ChatMentions() {
        super("ChatMentions", Category.MISC, "Notifies when your name, a friend, or a keyword appears in chat");
    }

    @Override
    public void selfSettings() {
        addBooleanSetting("ownName", true, "Notify when your own name is mentioned");
        addBooleanSetting("friendNames", false, "Notify when a friend's name is mentioned");
        settings.addSetting("keywords", "");
        addBooleanSetting("sound", true, "Play the notification cue for mentions");
        addNumberSetting("soundVolume", 0.6, 0.05, 1.0, "Mention cue volume");
        addNumberSetting("cooldown", 1000, 0, 10000, "Milliseconds between mention notifications");
    }

    /**
     * Decides whether a received chat line is a mention.
     *
     * @return the reason to show, or null when the line is not a mention
     */
    public String mentionReason(String message) {
        if (message == null || message.isBlank() || mc.player == null) return null;
        String self = mc.player.getName().getString();
        // Your own messages contain your name by definition; they are not mentions.
        if (ChatMatcher.containsWord(message, self) && startsWithOwnName(message, self)) return null;
        if (getBooleanSetting("ownName", true) && ChatMatcher.containsWord(message, self)) {
            return "You were mentioned in chat";
        }
        if (getBooleanSetting("friendNames", false) && AgalarHackClient.FRIEND_MANAGER != null) {
            for (String friend : AgalarHackClient.FRIEND_MANAGER.getFriends()) {
                if (ChatMatcher.containsWord(message, friend)) return friend + " was mentioned in chat";
            }
        }
        if (ChatMatcher.containsAny(message, keywords())) return "Keyword mentioned in chat";
        return null;
    }

    /**
     * Whether the line looks like the local player speaking.
     *
     * <p>Chat formats vary by server, so this only checks whether the name appears before any text
     * the player could have typed - enough to avoid notifying on your own messages without trying to
     * parse every possible format.
     */
    private static boolean startsWithOwnName(String message, String self) {
        int index = message.toLowerCase(java.util.Locale.ROOT).indexOf(self.toLowerCase(java.util.Locale.ROOT));
        if (index < 0) return false;
        String prefix = message.substring(0, index);
        return prefix.length() <= 4 && prefix.chars().noneMatch(Character::isLetterOrDigit);
    }

    /** Called by the client's chat bridge for every received message. */
    public void onChatMessage(String message) {
        String reason = mentionReason(message);
        if (reason == null) return;
        long now = System.currentTimeMillis();
        if (now - lastNotified < getNumberSetting("cooldown", 1000)) return;
        lastNotified = now;
        service(NotificationService.class).publish(NotificationService.Type.INFO, reason);
        if (getBooleanSetting("sound", true)) {
            NotificationSounds.play(NotificationService.Type.INFO, (float) getNumberSetting("soundVolume", 0.6));
        }
    }

    private Set<String> keywords() {
        String raw = getStringSetting("keywords", "");
        if (!raw.equals(parsedKeywords)) {
            keywords = ChatMatcher.parse(raw);
            parsedKeywords = raw;
        }
        return keywords;
    }

    @Override public boolean runsWithoutWorld() { return true; }
    @Override public void onDisable() { lastNotified = 0; }
}
