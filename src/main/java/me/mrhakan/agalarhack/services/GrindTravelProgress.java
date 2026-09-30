package me.mrhakan.agalarhack.services;

/** Bounded stall detection; path calculation and walking both count as travel. */
final class GrindTravelProgress {
    static final int STALL_TICKS = 20 * 60;
    private double x, y, z;
    private int idleTicks;
    private boolean started;

    boolean stalled(double nextX, double nextY, double nextZ) {
        double dx = nextX - x, dy = nextY - y, dz = nextZ - z;
        if (!started || dx * dx + dy * dy + dz * dz >= 1) {
            started = true; x = nextX; y = nextY; z = nextZ; idleTicks = 0;
        } else idleTicks++;
        return idleTicks >= STALL_TICKS;
    }
    void reset() { started = false; idleTicks = 0; }
}
