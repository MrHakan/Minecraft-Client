package me.mrhakan.agalarhack.ui;

import java.util.Locale;

/** Every word must match; subsequence matching allows abbreviations without edit-distance allocation. */
public final class ModuleSearch {
    private ModuleSearch() { }
    public static boolean matches(String query, String haystack) {
        if (query == null || query.isBlank()) return true;
        String text = haystack.toLowerCase(Locale.ROOT);
        for (String word : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (text.contains(word)) continue;
            int cursor = 0;
            for (int i=0;i<text.length() && cursor<word.length();i++) if (text.charAt(i)==word.charAt(cursor)) cursor++;
            if (cursor != word.length()) return false;
        }
        return true;
    }
}
