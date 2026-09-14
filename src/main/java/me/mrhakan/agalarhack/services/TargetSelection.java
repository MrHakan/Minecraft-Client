package me.mrhakan.agalarhack.services;

import java.util.Comparator;

/** Pure target geometry and stable priority policy; only observed values are accepted. */
public final class TargetSelection {
    private TargetSelection() { }
    public record Metrics(int id, double distanceSquared, double health, int armor, double angle,
                          double crosshairAngle, int hurtTime, boolean recentAttacker) { }
    public static double wrapDegrees(double degrees) {
        double value = degrees % 360;
        return value >= 180 ? value - 360 : value < -180 ? value + 360 : value;
    }
    public static boolean inRange(double distanceSquared, boolean visible, double range, double wallsRange,
                                  double yawDelta, double fov) {
        double allowed = visible ? range : Math.min(range, wallsRange);
        return Double.isFinite(distanceSquared) && distanceSquared >= 0 && allowed > 0
                && distanceSquared <= allowed * allowed && Math.abs(wrapDegrees(yawDelta)) <= fov / 2;
    }
    public static Comparator<Metrics> comparator(String priority) {
        Comparator<Metrics> order = switch (priority) {
            case "lowest_health" -> Comparator.comparingDouble(Metrics::health);
            case "highest_health" -> Comparator.comparingDouble(Metrics::health).reversed();
            case "lowest_armor" -> Comparator.comparingInt(Metrics::armor);
            case "angle" -> Comparator.comparingDouble(Metrics::angle);
            case "crosshair" -> Comparator.comparingDouble(Metrics::crosshairAngle);
            case "hurt_time" -> Comparator.comparingInt(Metrics::hurtTime);
            case "recent_attacker" -> Comparator.comparing(Metrics::recentAttacker).reversed();
            default -> Comparator.comparingDouble(Metrics::distanceSquared);
        };
        return order.thenComparingDouble(Metrics::distanceSquared).thenComparingInt(Metrics::id);
    }
}
