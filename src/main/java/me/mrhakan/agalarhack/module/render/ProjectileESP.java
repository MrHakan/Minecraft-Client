package me.mrhakan.agalarhack.module.render;

import java.util.List;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.EntityDiscovery;
import me.mrhakan.agalarhack.services.ScannerService;
import me.mrhakan.agalarhack.services.projectile.ProjectilePhysics;
import me.mrhakan.agalarhack.services.scanning.ScanScheduler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;

/** Highlights projectiles in flight and the direction they are travelling. */
public class ProjectileESP extends Module {
    private List<Entity> projectiles = List.of();

    public ProjectileESP() {
        super("ProjectileESP", Category.RENDER, "Highlights projectiles in flight with their direction of travel");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("range", 96.0, 8.0, 256.0, "Maximum render distance in blocks");
        addNumberSetting("maximumProjectiles", 64, 8, 256, "Maximum highlighted projectiles; nearest are kept");
        addBooleanSetting("boxes", true, "Draw a box around each projectile");
        addBooleanSetting("velocity", true, "Draw a short line along the direction of travel");
        addNumberSetting("velocityScale", 8.0, 1.0, 40.0, "Length of the direction line in ticks of travel");
        addBooleanSetting("tnt", true, "Include primed TNT");
        addNumberSetting("red", 255.0, 0.0, 255.0, "Overlay red channel");
        addNumberSetting("green", 90.0, 0.0, 255.0, "Overlay green channel");
        addNumberSetting("blue", 90.0, 0.0, 255.0, "Overlay blue channel");
        addNumberSetting("alpha", 220.0, 32.0, 255.0, "Overlay alpha channel");
    }

    public List<Entity> projectiles() { return projectiles; }

    @Override
    public void onDisable() {
        service(ScannerService.class).cancel(this);
        projectiles = List.of();
    }

    @Override
    public void onUpdate() {
        if (mc.player == null) { projectiles = List.of(); return; }
        boolean tnt = getBooleanSetting("tnt", true);
        EntityDiscovery.offer(this, service(ScannerService.class), ScanScheduler.Priority.NEAR,
                mc, (int) getNumberSetting("maximumProjectiles", 64), getNumberSetting("range", 96),
                entity -> tracked(entity, tnt) ? entity : null,
                result -> projectiles = result);
    }

    public static boolean tracked(Entity entity, boolean includeTnt) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity instanceof Projectile) return true;
        return includeTnt && entity instanceof PrimedTnt;
    }

    /**
     * Best-effort motion model for an observed projectile.
     *
     * <p>The client cannot know what a projectile actually is beyond its class, so throwables get the
     * gravity-before-drag order and everything else the arrow order. This is an estimate and any
     * prediction built on it must be labelled as one.
     */
    public static ProjectilePhysics observedPhysics(Entity entity) {
        boolean throwable = entity instanceof ThrowableProjectile;
        return new ProjectilePhysics(1.0, throwable ? 0.03 : 0.05, 0.99, 0.0, throwable);
    }
}
