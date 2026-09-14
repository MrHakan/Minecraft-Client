package me.mrhakan.agalarhack.services;

import java.util.function.IntToDoubleFunction;

/** Stable bounded selection; invalid/non-finite scores are never selected. */
public final class InventorySelection {
    private InventorySelection() { }
    public static int best(int slots, double minimumScore, IntToDoubleFunction score) {
        int best = -1;
        double value = minimumScore;
        for (int slot = 0; slot < Math.max(0, Math.min(36, slots)); slot++) {
            double candidate = score.applyAsDouble(slot);
            if (Double.isFinite(candidate) && candidate > value) {
                best = slot; value = candidate;
            }
        }
        return best;
    }
}
