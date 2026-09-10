package me.mrhakan.agalarhack.services;

public final class RotationMath {
    private RotationMath() { }
    public static float stepYaw(float current, float target, float maximum) {
        if (!Float.isFinite(current) || !Float.isFinite(target) || !Float.isFinite(maximum)) return current;
        double delta = TargetSelection.wrapDegrees(target - current);
        return current + (float) Math.max(-Math.max(0, maximum), Math.min(Math.max(0, maximum), delta));
    }
    public static float stepPitch(float current, float target, float maximum) {
        if (!Float.isFinite(current) || !Float.isFinite(target) || !Float.isFinite(maximum)) return current;
        target = Math.max(-90, Math.min(90, target));
        return Math.max(-90, Math.min(90, current + Math.max(-Math.max(0, maximum), Math.min(Math.max(0, maximum), target - current))));
    }
}
