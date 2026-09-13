package me.mrhakan.agalarhack.module.render;

import java.util.List;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.DeathWaypoints;
import me.mrhakan.agalarhack.services.NotificationService;
import me.mrhakan.agalarhack.services.Waypoint;
import me.mrhakan.agalarhack.services.WaypointService;
import net.minecraft.client.Minecraft;

/**
 * Renders saved positions for the dimension the player is currently in, and optionally records one
 * where you died.
 *
 * <p>Death recording lives here rather than on AutoRespawn because it is useful whether or not you
 * respawn automatically, and because this is the module that owns waypoints. It is off by default:
 * writing to a persistent store is not something a render module should start doing unasked.
 */
public class Waypoints extends Module {
    /**
     * Set the tick the player's health first reaches zero and cleared once they are alive again,
     * so one death writes one waypoint no matter how long the death screen stays up.
     */
    private boolean deathRecorded;

    public Waypoints() {
        super("Waypoints", Category.RENDER, "Shows saved waypoints for the current dimension with optional beams and labels");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("renderDistance", 512, 16, 4096, "Hide waypoints beyond this horizontal distance");
        addBooleanSetting("labels", true, "Show the waypoint name above its marker");
        addBooleanSetting("distanceInLabel", true, "Append the horizontal distance to the label");
        addBooleanSetting("beams", true, "Draw a vertical beam for waypoints that have one enabled");
        addNumberSetting("beamHeight", 256, 16, 1024, "Beam height in blocks");
        addNumberSetting("markerSize", 1.0, 0.25, 4.0, "Marker box size in blocks");
        addBooleanSetting("deathWaypoint", false, "Save a waypoint where you died");
        addNumberSetting("deathWaypointKeep", 3, 1, DeathWaypoints.MAX_KEEP,
                "How many death waypoints to keep; the oldest are dropped, hand-made waypoints never are");
    }

    /**
     * The module manager stops ticking modules once the player is no longer alive, which is exactly
     * when the death has to be recorded. The guards in {@link #onUpdate()} replace that check.
     */
    @Override public boolean runsWithoutWorld() { return true; }

    @Override public void onEnable() { deathRecorded = false; }
    @Override public void onDisable() { deathRecorded = false; }
    @Override public void onDisconnect() { deathRecorded = false; }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null) { deathRecorded = false; return; }
        if (mc.player.getHealth() > 0.0F) { deathRecorded = false; return; }
        if (deathRecorded || !getBooleanSetting("deathWaypoint", false)) return;
        deathRecorded = true;
        recordDeath();
    }

    private void recordDeath() {
        String dimension = currentDimension(mc);
        if (dimension == null) return;
        var position = mc.player.blockPosition();
        WaypointService waypoints = service(WaypointService.class);
        int keep = (int) Math.round(getNumberSetting("deathWaypointKeep", 3));
        DeathWaypoints.Plan plan = DeathWaypoints.plan(waypoints.all(),
                position.getX(), position.getY(), position.getZ(), dimension, keep);
        boolean stored = waypoints.apply(plan.remove(), plan.add());
        service(NotificationService.class).publish(
                stored ? NotificationService.Type.INFO : NotificationService.Type.WARNING,
                stored ? "Death waypoint saved: " + plan.add().name()
                        : "Waypoint store is full; death position not saved");
    }

    /** Current dimension id, or null when there is no world; 26.2 uses identifier(), not location(). */
    public static String currentDimension(Minecraft client) {
        return client.level == null ? null : client.level.dimension().identifier().toString();
    }

    /** Visible waypoints for the current dimension; empty when the module is off or the world is gone. */
    public List<Waypoint> visible() {
        String dimension = currentDimension(mc);
        if (dimension == null) return List.of();
        return service(WaypointService.class).visibleIn(dimension);
    }
}
