package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    /** One run of a message, and whether a term matched it. */
    public record Segment(String text, boolean matched) { }

    /**
     * Where {@code term} next appears as a whole word, or -1.
     *
     * <p>A word boundary here is anything that is not a letter, digit or underscore, which keeps
     * player names intact while still matching them next to punctuation.
     *
     * <p>Both the "is this about me" test and the inline highlighting read this, so a change to what
     * counts as a word cannot make the marker and the colouring disagree.
     */
    public static int indexOfWord(String message, String term, int from) {
        if (message == null || term == null || term.isEmpty()) return -1;
        String haystack = message.toLowerCase(Locale.ROOT);
        String needle = term.toLowerCase(Locale.ROOT);
        int at = Math.max(0, from);
        while (at <= haystack.length() - needle.length()) {
            int index = haystack.indexOf(needle, at);
            if (index < 0) return -1;
            boolean startOk = index == 0 || !isWordCharacter(haystack.charAt(index - 1));
            int end = index + needle.length();
            boolean endOk = end >= haystack.length() || !isWordCharacter(haystack.charAt(end));
            if (startOk && endOk) return index;
            at = index + 1;
        }
        return -1;
    }

    /** Whether {@code message} contains {@code term} as a whole word. */
    public static boolean containsWord(String message, String term) {
        return indexOfWord(message, term, 0) >= 0;
    }

    /**
     * Splits a message into alternating plain and matched runs, in order and losing nothing.
     *
     * <p>Where two terms match at the same place the longer one wins, so listing both "sam" and
     * "sammy" highlights the whole of a "sammy" rather than three of its letters.
     */
    public static List<Segment> highlight(String message, Set<String> terms) {
        if (message == null || message.isEmpty()) return List.of();
        if (terms == null || terms.isEmpty()) return List.of(new Segment(message, false));
        List<Segment> segments = new ArrayList<>();
        int cursor = 0;
        while (cursor < message.length()) {
            int bestAt = -1;
            int bestLength = 0;
            for (String term : terms) {
                int at = indexOfWord(message, term, cursor);
                if (at < 0) continue;
                if (bestAt < 0 || at < bestAt || (at == bestAt && term.length() > bestLength)) {
                    bestAt = at;
                    bestLength = term.length();
                }
            }
            if (bestAt < 0) {
                segments.add(new Segment(message.substring(cursor), false));
                break;
            }
            if (bestAt > cursor) segments.add(new Segment(message.substring(cursor, bestAt), false));
            segments.add(new Segment(message.substring(bestAt, bestAt + bestLength), true));
            cursor = bestAt + bestLength;
        }
        return List.copyOf(segments);
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
