package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * User-defined key-to-action bindings.
 *
 * <p>An action is a single line: chat text, a client command, or a module toggle. Sequences with
 * delays are deliberately absent - a macro that fires several actions over time is indistinguishable
 * from automation the player did not watch, and the roadmap only asks for local sequences, which
 * this leaves to the command layer.
 *
 * <p>Free of Minecraft types so parsing, validation and bounds are tested directly.
 */
public final class MacroDefinitions {
    public static final int MAX_MACROS = 32;
    public static final int MAX_ACTION_LENGTH = 256;

    /** What a macro does when its key fires. */
    public enum Kind {
        /** Sends the text to chat as the player typed it. */
        CHAT,
        /** Runs a client command, without the prefix. */
        COMMAND,
        /** Toggles a module by name. */
        TOGGLE
    }

    public record Macro(int key, boolean mouse, Kind kind, String action) { }

    private final Map<String, Macro> macros = new LinkedHashMap<>();

    private static String key(int key, boolean mouse) { return (mouse ? "m" : "k") + key; }

    /**
     * Defines or replaces the macro on a key.
     *
     * @return false when the key or action is unusable, or the store is full
     */
    public boolean define(int key, boolean mouse, Kind kind, String action) {
        if (kind == null || action == null) return false;
        String trimmed = action.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_ACTION_LENGTH) return false;
        if (key < 0 && !mouse) return false;
        String id = key(key, mouse);
        if (!macros.containsKey(id) && macros.size() >= MAX_MACROS) return false;
        macros.put(id, new Macro(key, mouse, kind, trimmed));
        return true;
    }

    public boolean remove(int key, boolean mouse) {
        return macros.remove(key(key, mouse)) != null;
    }

    public Macro find(int key, boolean mouse) {
        return macros.get(key(key, mouse));
    }

    public List<Macro> all() { return List.copyOf(new ArrayList<>(macros.values())); }

    public int size() { return macros.size(); }

    public void clear() { macros.clear(); }

    /** Replaces everything, keeping only entries that pass the same validation as {@link #define}. */
    public void replaceAll(List<Macro> stored) {
        macros.clear();
        if (stored == null) return;
        for (Macro macro : stored) {
            if (macro != null) define(macro.key(), macro.mouse(), macro.kind(), macro.action());
        }
    }

    public static Kind parseKind(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "chat", "say" -> Kind.CHAT;
            case "command", "cmd" -> Kind.COMMAND;
            case "toggle", "module" -> Kind.TOGGLE;
            default -> null;
        };
    }
}
