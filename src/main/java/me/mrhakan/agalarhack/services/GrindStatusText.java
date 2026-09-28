package me.mrhakan.agalarhack.services;

/** Allocation-light, Minecraft-free status text shared by AutoGrind chat and HUD surfaces. */
public final class GrindStatusText {
    private GrindStatusText() { }

    public static String format(TaskRunner.State state, String task, int completed, int total,
            String blockedReason, String failure) {
        if (state == null) return "AutoGrind idle";
        return switch (state) {
            case IDLE -> "AutoGrind idle";
            case DONE -> "AutoGrind complete";
            case FAILED -> "AutoGrind failed: " + valueOr(failure, "unknown error");
            case NEEDS_MOVEMENT -> "AutoGrind paused: "
                    + valueOr(blockedReason, "manual movement required");
            case RUNNING -> {
                int safeTotal = Math.max(1, total);
                int visibleStep = Math.min(safeTotal, Math.max(1, completed + 1));
                yield "AutoGrind " + visibleStep + "/" + safeTotal + ": "
                        + valueOr(task, "preparing next step");
            }
        };
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
