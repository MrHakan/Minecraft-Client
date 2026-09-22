package me.mrhakan.agalarhack.ui;

import java.util.Locale;

/** Allocation-conscious module search with word AND semantics and abbreviation matching. */
public final class ModuleSearch {
    private ModuleSearch() { }

    /** Normalizes catalogue text once so repeated searches do not lowercase every module every time. */
    public static String prepare(String haystack) {
        return haystack == null ? "" : haystack.toLowerCase(Locale.ROOT);
    }

    /** Normalizes a user query once before it is tested against a module catalogue. */
    public static String prepareQuery(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean matches(String query, String haystack) {
        return matchesPreparedQuery(prepareQuery(query), prepare(haystack));
    }

    /** Matches a raw query against text returned by {@link #prepare(String)}. */
    public static boolean matchesPrepared(String query, String preparedHaystack) {
        return matchesPreparedQuery(prepareQuery(query), preparedHaystack);
    }

    /**
     * Hot-path variant for catalogue searches: both query and haystack have already been normalized.
     * This avoids trimming/lowercasing the same query once per registered module.
     */
    public static boolean matchesPreparedQuery(String preparedQuery, String preparedHaystack) {
        if (preparedQuery == null || preparedQuery.isBlank()) return true;
        String text = preparedHaystack == null ? "" : preparedHaystack;
        int start = 0;
        while (start < preparedQuery.length()) {
            while (start < preparedQuery.length() && Character.isWhitespace(preparedQuery.charAt(start))) start++;
            if (start >= preparedQuery.length()) break;
            int end = start + 1;
            while (end < preparedQuery.length() && !Character.isWhitespace(preparedQuery.charAt(end))) end++;
            String word = preparedQuery.substring(start, end);
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
