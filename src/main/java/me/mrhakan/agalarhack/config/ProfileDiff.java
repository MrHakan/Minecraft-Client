package me.mrhakan.agalarhack.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Compares two profile snapshots setting by setting.
 *
 * <p>Profiles are whole-client snapshots, so "what is actually different between these two" is not
 * something you can read off the files: a profile saved a month apart from another differs in
 * hundreds of untouched defaults' worth of JSON ordering and nothing you care about. This reduces it
 * to the settings that genuinely changed value.
 *
 * <p>Number comparison is deliberately numeric rather than by object identity. Snapshots go through
 * Gson, which turns every number into a {@code Double}, while live settings hold whatever the module
 * put there — so {@code 3} and {@code 3.0} are the same setting written twice, and reporting that as
 * a change would bury the real ones.
 *
 * <p>Kept free of Minecraft and Gson types so the comparison rules are unit tested directly.
 */
public final class ProfileDiff {
    private ProfileDiff() { }

    public enum Kind {
        /** The module exists only in the right-hand snapshot. */
        MODULE_ADDED,
        /** The module exists only in the left-hand snapshot. */
        MODULE_REMOVED,
        /** The setting exists only in the right-hand snapshot. */
        SETTING_ADDED,
        /** The setting exists only in the left-hand snapshot. */
        SETTING_REMOVED,
        /** Both sides have the setting with different values. */
        CHANGED
    }

    /** @param setting null for the two module-level kinds */
    public record Change(Kind kind, String module, String setting, Object left, Object right) { }

    /**
     * @param left the snapshot being compared from
     * @param right the snapshot being compared to
     * @return changes ordered by module then setting, so repeated runs read the same way
     */
    public static List<Change> compare(Map<String, Map<String, Object>> left,
                                       Map<String, Map<String, Object>> right) {
        Map<String, Map<String, Object>> from = left == null ? Map.of() : left;
        Map<String, Map<String, Object>> to = right == null ? Map.of() : right;

        List<Change> changes = new ArrayList<>();
        Set<String> modules = new LinkedHashSet<>(from.keySet());
        modules.addAll(to.keySet());
        List<String> ordered = new ArrayList<>(modules);
        ordered.sort(String.CASE_INSENSITIVE_ORDER);

        for (String module : ordered) {
            Map<String, Object> before = from.get(module);
            Map<String, Object> after = to.get(module);
            if (before == null) { changes.add(new Change(Kind.MODULE_ADDED, module, null, null, null)); continue; }
            if (after == null) { changes.add(new Change(Kind.MODULE_REMOVED, module, null, null, null)); continue; }
            compareSettings(module, before, after, changes);
        }
        return List.copyOf(changes);
    }

    private static void compareSettings(String module, Map<String, Object> before, Map<String, Object> after,
                                        List<Change> changes) {
        Set<String> keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        List<String> ordered = new ArrayList<>(keys);
        ordered.sort(String.CASE_INSENSITIVE_ORDER);
        for (String key : ordered) {
            boolean hasBefore = before.containsKey(key);
            boolean hasAfter = after.containsKey(key);
            Object from = before.get(key);
            Object to = after.get(key);
            if (!hasBefore) changes.add(new Change(Kind.SETTING_ADDED, module, key, null, to));
            else if (!hasAfter) changes.add(new Change(Kind.SETTING_REMOVED, module, key, from, null));
            else if (!sameValue(from, to)) changes.add(new Change(Kind.CHANGED, module, key, from, to));
        }
    }

    /**
     * Settings equality as a player would judge it.
     *
     * <p>Numbers compare numerically across types, because a snapshot that has been through JSON
     * holds {@code Double} where a live setting holds whatever the module assigned. Booleans and
     * strings compare exactly — a setting whose value is the string "Enabled" is a choice the player
     * picked, and case there is not ours to normalise.
     */
    public static boolean sameValue(Object left, Object right) {
        if (left == null || right == null) return left == right;
        if (left instanceof Number a && right instanceof Number b) {
            double first = a.doubleValue();
            double second = b.doubleValue();
            // NaN never equals itself, but two settings both holding NaN are not a change to report.
            if (Double.isNaN(first) && Double.isNaN(second)) return true;
            return first == second;
        }
        return left.equals(right);
    }

    /** Human-readable value, so a diff line does not print "3.0" for a setting the player set to 3. */
    public static String describe(Object value) {
        if (value == null) return "-";
        if (value instanceof Number number) {
            double raw = number.doubleValue();
            if (raw == Math.rint(raw) && !Double.isInfinite(raw)) return String.valueOf((long) raw);
            return String.valueOf(raw);
        }
        return String.valueOf(value);
    }

    /**
     * One line per change, truncated with a count of what was left out.
     *
     * <p>The cap is not cosmetic: a diff between unrelated profiles can run to hundreds of lines, and
     * chat would drop most of it silently. Saying how many were hidden is the difference between a
     * truncated answer and a wrong one.
     */
    public static List<String> format(List<Change> changes, int maxLines) {
        int cap = Math.max(1, maxLines);
        List<String> lines = new ArrayList<>();
        for (Change change : changes) {
            if (lines.size() >= cap) {
                lines.add("... and " + (changes.size() - cap) + " more");
                break;
            }
            lines.add(line(change));
        }
        return List.copyOf(lines);
    }

    private static String line(Change change) {
        return switch (change.kind()) {
            case MODULE_ADDED -> "+ " + change.module();
            case MODULE_REMOVED -> "- " + change.module();
            case SETTING_ADDED -> "+ " + change.module() + "." + change.setting() + " = " + describe(change.right());
            case SETTING_REMOVED -> "- " + change.module() + "." + change.setting() + " (was " + describe(change.left()) + ")";
            case CHANGED -> change.module() + "." + change.setting() + ": "
                    + describe(change.left()) + " -> " + describe(change.right());
        };
    }

    /** Short one-line summary for the case where the caller only wants the headline. */
    public static String summarise(List<Change> changes) {
        if (changes.isEmpty()) return "identical";
        long modules = changes.stream()
                .filter(change -> change.kind() == Kind.MODULE_ADDED || change.kind() == Kind.MODULE_REMOVED)
                .count();
        long settings = changes.size() - modules;
        StringBuilder text = new StringBuilder();
        if (modules > 0) text.append(modules).append(modules == 1 ? " module" : " modules");
        if (settings > 0) {
            if (text.length() > 0) text.append(", ");
            text.append(settings).append(settings == 1 ? " setting" : " settings");
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }
}
