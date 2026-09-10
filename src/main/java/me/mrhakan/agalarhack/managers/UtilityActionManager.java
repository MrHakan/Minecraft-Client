package me.mrhakan.agalarhack.managers;

/**
 * Tiny per-tick ownership arbiter so automation modules do not fight over the
 * same hotbar/use controls. Higher-priority owners run first and retain the
 * channel for the rest of that client tick.
 */
public class UtilityActionManager {
    private String hotbarOwner;
    private int hotbarPriority = Integer.MIN_VALUE;
    private String useOwner;
    private int usePriority = Integer.MIN_VALUE;

    public void beginTick() {
        hotbarOwner = null;
        hotbarPriority = Integer.MIN_VALUE;
        useOwner = null;
        usePriority = Integer.MIN_VALUE;
    }

    public boolean claimHotbar(String owner, int priority) {
        if (hotbarOwner == null || hotbarOwner.equals(owner) || priority > hotbarPriority) {
            hotbarOwner = owner;
            hotbarPriority = priority;
            return true;
        }
        return false;
    }

    public boolean claimUse(String owner, int priority) {
        if (useOwner == null || useOwner.equals(owner) || priority > usePriority) {
            useOwner = owner;
            usePriority = priority;
            return true;
        }
        return false;
    }

    /** Atomic acquisition prevents a failed dual claim from reserving half the controls. */
    public boolean claimHotbarAndUse(String owner, int priority) {
        if (owner == null || owner.isBlank()) return false;
        boolean hotbar = hotbarOwner == null || hotbarOwner.equals(owner) || priority > hotbarPriority;
        boolean use = useOwner == null || useOwner.equals(owner) || priority > usePriority;
        if (!hotbar || !use) return false;
        claimHotbar(owner, priority);
        claimUse(owner, priority);
        return true;
    }

    public boolean ownsHotbar(String owner) {
        return owner != null && owner.equals(hotbarOwner);
    }

    public boolean ownsUse(String owner) {
        return owner != null && owner.equals(useOwner);
    }
}
