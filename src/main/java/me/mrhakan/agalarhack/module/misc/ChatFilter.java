package me.mrhakan.agalarhack.module.misc;

import java.util.Set;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.ChatMatcher;

/**
 * Hides chat lines matching user-listed phrases, or repeating a recent line, locally only.
 *
 * <p>This affects nothing but what the local client draws; no message is blocked from the server and
 * nobody else's view changes. The list is empty by default, so enabling the module alone hides
 * nothing - a filter that starts by silently swallowing messages would be worse than no filter.
 */
public class ChatFilter extends Module {
    private String parsedPatterns;
    private Set<String> patterns = Set.of();
    private final me.mrhakan.agalarhack.services.RepeatSuppressor repeats =
            new me.mrhakan.agalarhack.services.RepeatSuppressor();

    public ChatFilter() {
        super("ChatFilter", Category.MISC, "Hides chat lines containing phrases you list, and optionally repeated lines; local display only");
    }

    @Override
    public void selfSettings() {
        settings.addSetting("hide", "");
        addBooleanSetting("gameMessages", false, "Also filter system and game messages, not just player chat");
        addBooleanSetting("hideRepeats", false, "Hide a line that repeats one of the last few");
        addNumberSetting("repeatWindow", 4, 1,
                me.mrhakan.agalarhack.services.RepeatSuppressor.MAX_WINDOW,
                "How many recent lines a repeat is compared against");
    }

    @Override public void onEnable() { repeats.reset(); }
    @Override public void onDisable() { repeats.reset(); }
    /** A new server is a new conversation; the last one's lines must not hide this one's. */
    @Override public void onDisconnect() { repeats.reset(); }

    /** Called by the client's chat bridge; true hides the line from the local chat display. */
    public boolean shouldHide(String message, boolean gameMessage) {
        if (gameMessage && !getBooleanSetting("gameMessages", false)) return false;
        if (ChatMatcher.filtered(message, patterns())) return true;
        // Checked last, and only when asked for: isRepeat records what it sees, so calling it for a
        // line already hidden by a phrase would make the next genuine copy of that line invisible.
        return getBooleanSetting("hideRepeats", false)
                && repeats.isRepeat(message, (int) Math.round(getNumberSetting("repeatWindow", 4)));
    }

    private Set<String> patterns() {
        String raw = getStringSetting("hide", "");
        if (!raw.equals(parsedPatterns)) {
            patterns = ChatMatcher.parse(raw);
            parsedPatterns = raw;
        }
        return patterns;
    }

    @Override public boolean runsWithoutWorld() { return true; }
}
