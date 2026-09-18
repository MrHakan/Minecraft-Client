package me.mrhakan.agalarhack.services;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a chat line is a request you asked to answer automatically.
 *
 * <p>Vanilla has no teleport-request or party system; on the servers that do, a request arrives as a
 * chat line from a plugin. That makes any automatic reply a decision made from text an arbitrary
 * player can influence, which is the whole risk: someone types the trigger phrase and your client
 * runs a command. So the rules here are deliberately narrow.
 *
 * <p><b>Every</b> configured phrase must appear, not any of them. One phrase is a trap — "tpa" shows
 * up in ordinary chat — while requiring "has requested" and "teleport" together does not happen by
 * accident. And the message must name someone you have listed, so a stranger cannot trigger it at
 * all.
 *
 * <p>Free of Minecraft types so the rules are unit tested directly.
 */
public final class RequestMatcher {
    private RequestMatcher() { }

    /**
     * @param message the received chat line
     * @param phrases every phrase that must be present; an empty set never matches, because
     *                answering every line would be the worst possible default
     * @param allowed names that may trigger a reply; an empty set never matches for the same reason
     * @return the matched name, or null when the line is not an answerable request
     */
    public static String match(String message, Set<String> phrases, List<String> allowed) {
        if (message == null || message.isBlank()) return null;
        if (phrases == null || phrases.isEmpty()) return null;
        if (allowed == null || allowed.isEmpty()) return null;

        String haystack = message.toLowerCase(Locale.ROOT);
        for (String phrase : phrases) {
            if (phrase.isEmpty() || !haystack.contains(phrase)) return null;
        }
        for (String name : allowed) {
            if (name != null && !name.isBlank() && ChatMatcher.containsWord(message, name)) return name;
        }
        return null;
    }

    /**
     * Substitutes the requester's name into the reply.
     *
     * <p>Only {@code {name}} is substituted, and only once per occurrence: a reply is sent to a
     * server as a command, so anything cleverer here is a way to send something unintended.
     */
    public static String fillReply(String template, String name) {
        if (template == null) return "";
        return template.replace("{name}", name == null ? "" : name).trim();
    }

    /**
     * A reply must be a command, and a single line.
     *
     * <p>Refusing plain text is not fussiness: an automatic reply that is not a command is a message
     * broadcast to the server every time someone says the trigger phrase. Refusing newlines stops a
     * crafted name turning one reply into several.
     */
    public static boolean isSendableReply(String reply) {
        return reply != null
                && reply.startsWith("/")
                && reply.length() > 1
                && reply.length() <= 256
                && reply.indexOf('\n') < 0
                && reply.indexOf('\r') < 0;
    }
}
