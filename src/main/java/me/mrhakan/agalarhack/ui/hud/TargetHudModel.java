package me.mrhakan.agalarhack.ui.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Decides what a target card shows and how its health bar moves.
 *
 * <p>Kept free of Minecraft types so the layout rules and the bar easing are unit tested. The
 * renderer is left with placement only.
 */
public final class TargetHudModel {
    private TargetHudModel() { }

    /** How much of the card is shown. */
    public enum Layout {
        /** Name and health bar only. */
        MINIMAL,
        /** The default: name, health, distance. */
        COMPACT,
        /** Everything the client knows, including ping and armour. */
        DETAILED;

        public static Layout parse(String raw) {
            if (raw == null) return COMPACT;
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "minimal" -> MINIMAL;
                case "detailed" -> DETAILED;
                default -> COMPACT;
            };
        }
    }

    /**
     * What the client currently knows about the target.
     *
     * @param ping milliseconds, or -1 when unknown; only players have one
     */
    public record Target(String name, float health, float maxHealth, float absorption,
                         double distance, int armor, int ping, boolean player,
                         boolean friend, boolean hurt) { }

    /** Text lines for a layout, in display order. The first line is always the name. */
    public static List<String> lines(Layout layout, Target target, boolean showHealth,
            boolean showDistance, boolean showArmor) {
        List<String> lines = new ArrayList<>();
        if (target == null) return lines;
        lines.add((target.friend() ? "★ " : "") + target.name());
        if (layout == Layout.MINIMAL) return List.copyOf(lines);

        if (showHealth) {
            float effective = target.health() + Math.max(0, target.absorption());
            String health = String.format(Locale.ROOT, "HP %.1f / %.1f", effective, target.maxHealth());
            // Absorption can exceed max health, so it is called out rather than silently overflowing.
            if (target.absorption() > 0) {
                lines.add(health + String.format(Locale.ROOT, " (+%.1f)", target.absorption()));
            } else {
                lines.add(health);
            }
        }
        if (showDistance) lines.add(String.format(Locale.ROOT, "Distance %.1fm", target.distance()));
        if (layout != Layout.DETAILED) return List.copyOf(lines);

        if (showArmor && target.player()) lines.add("Armor " + target.armor());
        if (target.player() && target.ping() >= 0) lines.add("Ping " + target.ping() + " ms");
        return List.copyOf(lines);
    }

    /** Equipment and effect rows only appear on the fuller layouts. */
    public static boolean showsIcons(Layout layout) { return layout != Layout.MINIMAL; }

    /**
     * Health bar fill, including absorption, clamped to 0..1.
     *
     * <p>Absorption is counted toward the same bar rather than overflowing it, so a target on
     * twenty absorption hearts reads as full rather than as several bars' worth.
     */
    public static double healthFraction(Target target) {
        if (target == null || target.maxHealth() <= 0) return 0;
        double effective = target.health() + Math.max(0, target.absorption());
        return Math.max(0.0, Math.min(1.0, effective / target.maxHealth()));
    }

    /**
     * Eases the displayed bar toward its true value.
     *
     * @param speed fraction of the remaining gap closed per frame, 0..1
     * @param animate false reports the target value immediately, for reduced motion
     */
    public static double easeToward(double displayed, double actual, double speed, boolean animate) {
        double goal = Double.isFinite(actual) ? Math.max(0, Math.min(1, actual)) : 0;
        if (!animate || !Double.isFinite(displayed)) return goal;
        double step = Double.isFinite(speed) ? Math.max(0, Math.min(1, speed)) : 1;
        double current = Math.max(0, Math.min(1, displayed));
        double next = current + (goal - current) * step;
        // Snap once the remaining gap is invisible, so the bar cannot creep forever.
        return Math.abs(goal - next) < 0.001 ? goal : next;
    }

    /** Bar colour thresholds, shared so the bar and any text tint agree. */
    public static int barColor(double fraction, boolean hurt) {
        if (hurt) return 0xFFFF8888;
        if (fraction > 0.6) return 0xFF55DD55;
        if (fraction > 0.3) return 0xFFFFCC44;
        return 0xFFFF5555;
    }
}
