package me.mrhakan.agalarhack.managers;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/** Shared last-valid-target state used by combat modules and TargetHUD. */
public class TargetTracker {
    private LivingEntity target;
    private long lastSeenMs;

    public void set(LivingEntity target) {
        if (target != null && target.isAlive() && !target.isRemoved()) {
            this.target = target;
            this.lastSeenMs = System.currentTimeMillis();
        }
    }

    public void clear() {
        target = null;
        lastSeenMs = 0L;
    }

    public LivingEntity get() {
        Minecraft mc = Minecraft.getInstance();
        if (target == null || mc.level == null || target.isRemoved() || !target.isAlive()) {
            clear();
            return null;
        }
        if (System.currentTimeMillis() - lastSeenMs > 3000L) {
            clear();
            return null;
        }
        return target;
    }
}
