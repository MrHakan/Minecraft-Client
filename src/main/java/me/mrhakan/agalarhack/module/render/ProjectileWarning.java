package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.NotificationService;
import me.mrhakan.agalarhack.services.projectile.ProjectileSimulator;
import net.minecraft.world.entity.Entity;

/**
 * Warns when a projectile already in flight looks like it will pass close to the player.
 *
 * <p>Informational only: it changes nothing about the player's movement or aim. The prediction
 * extrapolates an observed velocity with an estimated motion model, so the wording says "estimate"
 * and the module never claims to know what the server will do.
 */
public class ProjectileWarning extends Module {
    private int cooldown;
    private int lastWarnedId = -1;

    public ProjectileWarning() {
        super("ProjectileWarning", Category.RENDER, "Estimates whether an incoming projectile will pass close to you");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("radius", 2.5, 0.5, 16.0, "Warn when the estimated path passes within this distance");
        addNumberSetting("lookahead", 60, 5, 200, "Ticks of flight to extrapolate");
        addNumberSetting("cooldown", 40, 5, 200, "Ticks between warnings");
        addBooleanSetting("ignoreOwnProjectiles", true, "Ignore projectiles you fired yourself");
    }

    @Override
    public void onEnable() { cooldown = 0; lastWarnedId = -1; }

    @Override
    public void onDisable() { cooldown = 0; lastWarnedId = -1; }

    @Override public void onDisconnect() { onDisable(); }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null) return;
        if (cooldown > 0) { cooldown--; return; }

        var espModule = me.mrhakan.agalarhack.AgalarHackClient.moduleManager.getModule("ProjectileESP");
        if (!(espModule instanceof ProjectileESP esp) || !esp.isToggled()) return;

        double radius = getNumberSetting("radius", 2.5);
        int lookahead = (int) Math.round(getNumberSetting("lookahead", 60));
        boolean ignoreOwn = getBooleanSetting("ignoreOwnProjectiles", true);

        for (Entity projectile : esp.projectiles()) {
            if (!projectile.isAlive() || projectile.getId() == lastWarnedId) continue;
            if (ignoreOwn && ownedByPlayer(projectile)) continue;
            var motion = projectile.getDeltaMovement();
            // A projectile that is barely moving is not incoming.
            if (motion.lengthSqr() < 0.01) continue;
            var state = new ProjectileSimulator.State(projectile.getX(), projectile.getY(), projectile.getZ(),
                    motion.x, motion.y, motion.z);
            var approach = ProjectileSimulator.closestApproach(state, ProjectileESP.observedPhysics(projectile),
                    mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), lookahead);
            if (approach.ticks() <= 0 || approach.distance() > radius) continue;
            warn(projectile, approach);
            return;
        }
    }

    private boolean ownedByPlayer(Entity projectile) {
        return projectile instanceof net.minecraft.world.entity.projectile.Projectile shot
                && shot.getOwner() == mc.player;
    }

    private void warn(Entity projectile, ProjectileSimulator.Approach approach) {
        lastWarnedId = projectile.getId();
        cooldown = (int) Math.round(getNumberSetting("cooldown", 40));
        String direction = compass(projectile);
        service(NotificationService.class).publish(NotificationService.Type.WARNING,
                "Incoming " + projectile.getType().getDescription().getString()
                        + " from " + direction + ", ~" + String.format(java.util.Locale.ROOT, "%.1f", approach.ticks() / 20.0)
                        + "s (estimate)");
    }

    /** Direction the projectile is coming from, relative to where the player is looking. */
    private String compass(Entity projectile) {
        double bearing = me.mrhakan.agalarhack.services.WaypointCompass.relativeBearing(
                mc.player.getX(), mc.player.getZ(), projectile.getX(), projectile.getZ(), mc.player.getYRot());
        double absolute = Math.abs(bearing);
        if (absolute <= 45) return "ahead";
        if (absolute >= 135) return "behind";
        return bearing > 0 ? "the right" : "the left";
    }
}
