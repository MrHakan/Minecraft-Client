package me.mrhakan.agalarhack.services;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Text matching for chat mentions and client-side filtering.
 *
 * <p>Matching is whole-word and case-insensitive, because a substring match on a player name fires
 * on half the server: "Sam" would match "Samantha" and "flotsam". Patterns are bounded and free of
 * regular expressions, so a pasted pattern cannot become a pathological match.
 *
 * <p>Free of Minecraft types so the rules are unit tested directly.
 */
public final class ChatMatcher {
    private ChatMatcher() { }

    public static final int MAX_PATTERNS = 32;
    public static final int MAX_PATTERN_LENGTH = 64;

    /** Splits a comma or newline separated list into bounded, lower-cased terms. */
    public static Set<String> parse(String raw) {
        Set<String> terms = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) return terms;
        for (String part : raw.split("[,\\n]")) {
            String term = part.trim().toLowerCase(Locale.ROOT);
            if (term.isEmpty() || term.length() > MAX_PATTERN_LENGTH) continue;
            terms.add(term);
            if (terms.size() >= MAX_PATTERNS) break;
        }
        return terms;
    }

    /**
     * Whether {@code message} contains {@code term} as a whole word.
     *
     * <p>A word boundary here is anything that is not a letter, digit or underscore, which keeps
     * player names intact while still matching them next to punctuation.
     */
    public static boolean containsWord(String message, String term) {
        if (message == null || term == null || term.isEmpty()) return false;
        String haystack = message.toLowerCase(Locale.ROOT);
        String needle = term.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int index = haystack.indexOf(needle, from);
            if (index < 0) return false;
            boolean startOk = index == 0 || !isWordCharacter(haystack.charAt(index - 1));
            int end = index + needle.length();
            boolean endOk = end >= haystack.length() || !isWordCharacter(haystack.charAt(end));
            if (startOk && endOk) return true;
            from = index + 1;
        }
    }

    /** True when any term appears as a whole word. */
    public static boolean containsAny(String message, Set<String> terms) {
        if (message == null || terms == null) return false;
        for (String term : terms) {
            if (containsWord(message, term)) return true;
        }
        return false;
    }

    /**
     * Whether a message should be hidden.
     *
     * <p>Filtering uses plain substring containment rather than whole-word matching, because a
     * filter is normally aimed at a phrase or a spam fragment rather than a name.
     */
    public static boolean filtered(String message, Set<String> patterns) {
        if (message == null || patterns == null || patterns.isEmpty()) return false;
        String haystack = message.toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (!pattern.isEmpty() && haystack.contains(pattern)) return true;
        }
        return false;
    }

    private static boolean isWordCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }
}
