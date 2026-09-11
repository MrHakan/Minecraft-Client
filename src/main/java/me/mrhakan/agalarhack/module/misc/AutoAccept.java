package me.mrhakan.agalarhack.module.misc;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.ChatMatcher;
import me.mrhakan.agalarhack.services.NotificationService;
import me.mrhakan.agalarhack.services.RequestMatcher;

/**
 * Answers a teleport or party request from someone you have listed.
 *
 * <p>Vanilla has no request system; on the servers that do, a request is a chat line from a plugin.
 * That makes any automatic reply a decision taken from text an arbitrary player can influence, so
 * this is built to be hard to abuse rather than convenient:
 *
 * <ul>
 *   <li>friends only by default, and never anyone unlisted;</li>
 *   <li>every configured phrase must appear, not any of them — one phrase is a trap;</li>
 *   <li>the reply must be a command the player typed themselves, and cannot be turned into two by a
 *       crafted name;</li>
 *   <li>a cooldown, so a repeated line cannot become a flood of commands;</li>
 *   <li>off by default, with nothing configured, so enabling it alone does nothing.</li>
 * </ul>
 *
 * <p>The formats differ per server, which is why the phrases are the player's to set rather than
 * guessed at here. A wrong guess would mean either never firing or firing on the wrong line.
 */
public class AutoAccept extends Module {
    private String parsedPhrases;
    private Set<String> phrases = Set.of();
    private long lastReply;

    public AutoAccept() {
        super("AutoAccept", Category.MISC, "Answers teleport or party requests from listed players with a command you choose");
        markExperimental();
    }

    @Override
    public void selfSettings() {
        // Empty by default: enabling the module alone must not answer anything.
        settings.addSetting("phrases", "");
        settings.addSetting("reply", "");
        addBooleanSetting("friendsOnly", true, "Only answer players in the friend list");
        settings.addSetting("allowedNames", "");
        addNumberSetting("cooldownSeconds", 5.0, 1.0, 120.0, "Minimum time between replies");
        addBooleanSetting("notify", true, "Say when a request was answered");
    }

    @Override public void onDisable() { lastReply = 0; }
    @Override public void onDisconnect() { lastReply = 0; }

    /** Terms the message must contain, re-parsed only when the setting text changes. */
    private Set<String> phrases() {
        String raw = getStringSetting("phrases", "");
        if (!raw.equals(parsedPhrases)) {
            phrases = ChatMatcher.parse(raw);
            parsedPhrases = raw;
        }
        return phrases;
    }

    /** Friends, plus any explicitly listed names; never everyone. */
    private List<String> allowedNames() {
        List<String> names = new java.util.ArrayList<>();
        if (getBooleanSetting("friendsOnly", true) && AgalarHackClient.FRIEND_MANAGER != null) {
            names.addAll(AgalarHackClient.FRIEND_MANAGER.getFriends());
        }
        for (String extra : getStringSetting("allowedNames", "").split("[,\\n]")) {
            String name = extra.trim();
            if (!name.isEmpty()) names.add(name);
        }
        return names;
    }

    /**
     * Called for every received chat line.
     *
     * @return the command that was sent, or null when nothing was answered
     */
    public String onChatMessage(String message) {
        if (mc.player == null) return null;
        long now = System.currentTimeMillis();
        long cooldown = (long) (getNumberSetting("cooldownSeconds", 5.0) * 1000);
        if (now - lastReply < cooldown) return null;

        String template = getStringSetting("reply", "");
        if (template.isBlank()) return null;

        String requester = RequestMatcher.match(message, phrases(), allowedNames());
        if (requester == null) return null;

        String reply = RequestMatcher.fillReply(template, requester);
        if (!RequestMatcher.isSendableReply(reply)) return null;

        // A player's own name could be in the list; answering your own message would be a loop.
        if (requester.equalsIgnoreCase(mc.player.getName().getString())) return null;

        lastReply = now;
        // sendCommand takes the command without its leading slash.
        mc.player.connection.sendCommand(reply.substring(1));
        if (getBooleanSetting("notify", true)) {
            service(NotificationService.class).publish(NotificationService.Type.INFO,
                    "Answered " + requester + " with " + reply);
        }
        return reply;
    }

    /** Lower-cased for the module list suffix; nothing here depends on it. */
    public String describeConfiguration() {
        return (phrases().isEmpty() ? "no phrases" : phrases().size() + " phrases")
                .toLowerCase(Locale.ROOT);
    }
}
