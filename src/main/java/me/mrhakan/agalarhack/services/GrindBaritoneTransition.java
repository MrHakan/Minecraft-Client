package me.mrhakan.agalarhack.services;

/** Let a cancelled owned process finish a safe movement before requesting the next process. */
final class GrindBaritoneTransition {
    private int remaining;
    void cancelled() { remaining = 100; }
    boolean waiting(boolean pathing, boolean mining, boolean goal) {
        if (!pathing || mining || goal) { remaining = 0; return false; }
        if (remaining == 0) return false;
        remaining--;
        return true;
    }
}
