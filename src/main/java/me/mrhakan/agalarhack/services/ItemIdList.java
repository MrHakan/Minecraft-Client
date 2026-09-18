package me.mrhakan.agalarhack.services;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Parses a user-entered list of item or block registry ids.
 *
 * <p>Settings are free text, so this is where a typo becomes harmless rather than dangerous:
 * entries are trimmed, lower-cased, given the {@code minecraft:} namespace when omitted, bounded in
 * both count and length, and anything that cannot be a registry id is dropped rather than guessed at.
 *
 * <p>Kept free of Minecraft types so the parsing rules are unit tested directly.
 */
public final class ItemIdList {
    private ItemIdList() { }

    public static final int MAX_ENTRIES = 128;
    private static final int MAX_LENGTH = 128;
    public static final String DEFAULT_NAMESPACE = "minecraft";

    /** @return an ordered, de-duplicated set of canonical ids; never null */
    public static Set<String> parse(String raw) {
        Set<String> ids = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) return ids;
        for (String part : raw.split("[,\\n;]")) {
            String id = canonical(part);
            if (id == null) continue;
            ids.add(id);
            if (ids.size() >= MAX_ENTRIES) break;
        }
        return ids;
    }

    /** @return the canonical {@code namespace:path} form, or null when the entry is unusable */
    public static String canonical(String entry) {
        if (entry == null) return null;
        String trimmed = entry.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) return null;
        int separator = trimmed.indexOf(':');
        String namespace = separator < 0 ? DEFAULT_NAMESPACE : trimmed.substring(0, separator);
        String path = separator < 0 ? trimmed : trimmed.substring(separator + 1);
        if (namespace.isEmpty() || path.isEmpty()) return null;
        if (!isValid(namespace, false) || !isValid(path, true)) return null;
        return namespace + ":" + path;
    }

    /** Mirrors the character set Minecraft accepts for resource locations. */
    private static boolean isValid(String value, boolean allowSlash) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean ok = (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')
                    || character == '_' || character == '-' || character == '.'
                    || (allowSlash && character == '/');
            if (!ok) return false;
        }
        return true;
    }
}
