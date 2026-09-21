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
    private String containerOwner;
    private int containerPriority = Integer.MIN_VALUE;

    public void beginTick() {
        hotbarOwner = null;
        hotbarPriority = Integer.MIN_VALUE;
        useOwner = null;
        usePriority = Integer.MIN_VALUE;
        containerOwner = null;
        containerPriority = Integer.MIN_VALUE;
    }

    public boolean claimHotbar(String owner, int priority) {
        if (owner == null || owner.isBlank()) return false;
        if (hotbarOwner == null || hotbarOwner.equals(owner) || priority > hotbarPriority) {
            hotbarPriority = owner.equals(hotbarOwner) ? Math.max(hotbarPriority, priority) : priority;
            hotbarOwner = owner;
            return true;
        }
        return false;
    }

    public boolean claimUse(String owner, int priority) {
        if (owner == null || owner.isBlank()) return false;
        if (useOwner == null || useOwner.equals(owner) || priority > usePriority) {
            usePriority = owner.equals(useOwner) ? Math.max(usePriority, priority) : priority;
            useOwner = owner;
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

    /** Separate channel for inventory-menu click sequences, which outlive a single tick. */
    public boolean claimContainer(String owner, int priority) {
        if (owner == null || owner.isBlank()) return false;
        if (containerOwner == null || containerOwner.equals(owner) || priority > containerPriority) {
            containerPriority = owner.equals(containerOwner) ? Math.max(containerPriority, priority) : priority;
            containerOwner = owner;
            return true;
        }
        return false;
    }

    public boolean ownsContainer(String owner) {
        return owner != null && owner.equals(containerOwner);
    }

    public boolean ownsHotbar(String owner) {
        return owner != null && owner.equals(hotbarOwner);
    }

    public boolean ownsUse(String owner) {
        return owner != null && owner.equals(useOwner);
    }
}
