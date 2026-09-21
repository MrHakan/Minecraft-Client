package me.mrhakan.agalarhack.ui;

import java.util.Locale;

/** Allocation-conscious module search with word AND semantics and abbreviation matching. */
public final class ModuleSearch {
    private ModuleSearch() { }

    /** Normalizes catalogue text once so repeated searches do not lowercase every module every time. */
    public static String prepare(String haystack) {
        return haystack == null ? "" : haystack.toLowerCase(Locale.ROOT);
    }

    public static boolean matches(String query, String haystack) {
        return matchesPrepared(query, prepare(haystack));
    }

    /** Matches against text returned by {@link #prepare(String)}. */
    public static boolean matchesPrepared(String query, String preparedHaystack) {
        if (query == null || query.isBlank()) return true;
        String text = preparedHaystack == null ? "" : preparedHaystack;
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        int start = 0;
        while (start < normalized.length()) {
            while (start < normalized.length() && Character.isWhitespace(normalized.charAt(start))) start++;
            if (start >= normalized.length()) break;
            int end = start + 1;
            while (end < normalized.length() && !Character.isWhitespace(normalized.charAt(end))) end++;
            String word = normalized.substring(start, end);
            if (!text.contains(word) && !isSubsequence(word, text)) return false;
            start = end;
        }
        return true;
    }

    private static boolean isSubsequence(String needle, String text) {
        int cursor = 0;
        for (int i = 0; i < text.length() && cursor < needle.length(); i++) {
            if (text.charAt(i) == needle.charAt(cursor)) cursor++;
        }
        return cursor == needle.length();
    }
}
