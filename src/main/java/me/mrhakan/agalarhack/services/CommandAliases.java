package me.mrhakan.agalarhack.services;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * User-defined shorthands for longer client commands.
 *
 * <p>Expansion is deliberately single-pass: an alias expands to a command, and the result is not
 * expanded again. That removes any possibility of an alias loop, at the cost of aliases that chain -
 * which is the right trade for something a user edits by hand.
 *
 * <p>Free of Minecraft types so the naming rules, the argument handling and the loop safety are
 * tested directly.
 */
public final class CommandAliases {
    public static final int MAX_ALIASES = 64;
    public static final int MAX_NAME = 32;
    public static final int MAX_EXPANSION = 256;

    private final Map<String, String> aliases = new LinkedHashMap<>();

    /**
     * Defines or replaces an alias.
     *
     * @return false when the name or expansion is unusable, or the store is full
     */
    public boolean define(String name, String expansion) {
        String key = normalise(name);
        if (key == null || expansion == null) return false;
        String value = expansion.trim();
        if (value.isEmpty() || value.length() > MAX_EXPANSION) return false;
        // An alias naming itself would expand into itself; reject it rather than rely on single-pass.
        if (normalise(firstWord(value)) != null && normalise(firstWord(value)).equals(key)) return false;
        if (!aliases.containsKey(key) && aliases.size() >= MAX_ALIASES) return false;
        aliases.put(key, value);
        return true;
    }

    public boolean remove(String name) {
        String key = normalise(name);
        return key != null && aliases.remove(key) != null;
    }

    public Map<String, String> all() { return Map.copyOf(aliases); }

    public int size() { return aliases.size(); }

    public void clear() { aliases.clear(); }

    /**
     * Replaces the whole set, keeping only entries that pass the same validation as {@link #define}.
     * Used when loading from disk, where the file may have been edited by hand.
     */
    public void replaceAll(Map<String, String> stored) {
        aliases.clear();
        if (stored == null) return;
        stored.forEach(this::define);
    }

    /**
     * Expands a command line if its first word is an alias.
     *
     * <p>Any arguments the user typed after the alias are appended to the expansion, so
     * {@code .home extra} with {@code home -> waypoint goto base} becomes
     * {@code waypoint goto base extra}.
     *
     * @return the expanded line, or empty when the first word is not an alias
     */
    public Optional<String> expand(String commandLine) {
        if (commandLine == null) return Optional.empty();
        String trimmed = commandLine.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        String head = firstWord(trimmed);
        String key = normalise(head);
        if (key == null) return Optional.empty();
        String expansion = aliases.get(key);
        if (expansion == null) return Optional.empty();
        String rest = trimmed.substring(head.length()).trim();
        return Optional.of(rest.isEmpty() ? expansion : expansion + " " + rest);
    }

    private static String firstWord(String value) {
        int space = value.indexOf(' ');
        return space < 0 ? value : value.substring(0, space);
    }

    /** Alias names are single lower-case words; anything else is rejected rather than coerced. */
    private static String normalise(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME) return null;
        for (int index = 0; index < trimmed.length(); index++) {
            char character = trimmed.charAt(index);
            boolean ok = (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')
                    || character == '_' || character == '-';
            if (!ok) return null;
        }
        return trimmed;
    }
}
