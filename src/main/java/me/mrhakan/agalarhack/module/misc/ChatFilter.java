package me.mrhakan.agalarhack.module.misc;

import java.util.Set;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.ChatMatcher;

/**
 * Hides chat lines matching user-listed phrases, locally only.
 *
 * <p>This affects nothing but what the local client draws; no message is blocked from the server and
 * nobody else's view changes. The list is empty by default, so enabling the module alone hides
 * nothing - a filter that starts by silently swallowing messages would be worse than no filter.
 */
public class ChatFilter extends Module {
    private String parsedPatterns;
    private Set<String> patterns = Set.of();

    public ChatFilter() {
        super("ChatFilter", Category.MISC, "Hides chat lines containing phrases you list; local display only");
    }

    @Override
    public void selfSettings() {
        settings.addSetting("hide", "");
        addBooleanSetting("gameMessages", false, "Also filter system and game messages, not just player chat");
    }

    /** Called by the client's chat bridge; true hides the line from the local chat display. */
    public boolean shouldHide(String message, boolean gameMessage) {
        if (gameMessage && !getBooleanSetting("gameMessages", false)) return false;
        return ChatMatcher.filtered(message, patterns());
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
