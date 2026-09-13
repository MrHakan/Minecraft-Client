package me.mrhakan.agalarhack.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which parts of a profile a partial load should touch.
 *
 * <p>A whole-profile load is a blunt instrument: taking one server's HUD layout means taking its
 * combat settings too, and copying a friend's KillAura tuning means losing your own keybinds. This
 * narrows a load to named modules, whole categories, the HUD, or the target policy, and leaves
 * everything else exactly as it is.
 *
 * <p>Module and category names are not known here — the module registry owns those — so a token is
 * simply matched against both. That keeps this class free of Minecraft types and testable, and it
 * means {@link #unknown} exists so a caller can report a typo rather than silently applying nothing,
 * which is the failure mode that makes a feature like this untrustworthy.
 */
public final class ProfileSelection {
    /** Tokens with a fixed meaning, so a module may not shadow them. */
    public static final String ALL = "all";
    public static final String MODULES = "modules";
    public static final String HUD = "hud";
    public static final String TARGETS = "targets";

    private static final Set<String> RESERVED = Set.of(ALL, MODULES, HUD, TARGETS);

    private final Set<String> tokens;

    private ProfileSelection(Set<String> tokens) {
        this.tokens = tokens;
    }

    /** Everything, which is what a plain load has always meant. */
    public static ProfileSelection everything() {
        return new ProfileSelection(Set.of(ALL));
    }

    /**
     * @param spec comma or space separated tokens; blank or null means everything, so an existing
     *             {@code .profile load name} keeps behaving exactly as it did
     */
    public static ProfileSelection parse(String spec) {
        if (spec == null || spec.isBlank()) return everything();
        Set<String> parsed = new LinkedHashSet<>();
        for (String part : spec.split("[,\\s]+")) {
            String token = part.trim().toLowerCase(Locale.ROOT);
            if (!token.isEmpty()) parsed.add(token);
        }
        if (parsed.isEmpty() || parsed.contains(ALL)) return everything();
        return new ProfileSelection(Set.copyOf(parsed));
    }

    public boolean isEverything() {
        return tokens.contains(ALL);
    }

    public boolean includesHud() {
        return isEverything() || tokens.contains(HUD);
    }

    public boolean includesTargetPolicy() {
        return isEverything() || tokens.contains(TARGETS);
    }

    /**
     * @param module the module's own name
     * @param category its category name, so {@code combat} selects every combat module
     */
    public boolean includesModule(String module, String category) {
        if (isEverything() || tokens.contains(MODULES)) return true;
        return (module != null && tokens.contains(module.toLowerCase(Locale.ROOT)))
                || (category != null && tokens.contains(category.toLowerCase(Locale.ROOT)));
    }

    /** True when the selection would change nothing at all, which is worth telling the player. */
    public boolean isEmpty(Collection<String> moduleNames, Collection<String> categoryNames) {
        if (includesHud() || includesTargetPolicy()) return false;
        if (isEverything() || tokens.contains(MODULES)) return false;
        for (String module : moduleNames) {
            if (tokens.contains(module.toLowerCase(Locale.ROOT))) return false;
        }
        for (String category : categoryNames) {
            if (tokens.contains(category.toLowerCase(Locale.ROOT))) return false;
        }
        return true;
    }

    /**
     * Tokens that match no reserved word, module or category.
     *
     * @return the offending tokens in the order they were written, so the message names the typo
     */
    public List<String> unknown(Collection<String> moduleNames, Collection<String> categoryNames) {
        Set<String> known = new LinkedHashSet<>(RESERVED);
        for (String module : moduleNames) known.add(module.toLowerCase(Locale.ROOT));
        for (String category : categoryNames) known.add(category.toLowerCase(Locale.ROOT));
        List<String> unknown = new ArrayList<>();
        for (String token : tokens) {
            if (!known.contains(token)) unknown.add(token);
        }
        return List.copyOf(unknown);
    }

    @Override
    public String toString() {
        return isEverything() ? ALL : String.join(", ", tokens);
    }
}
