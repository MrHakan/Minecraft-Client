package me.mrhakan.agalarhack.module.world;

import java.util.ArrayList;
import java.util.List;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.module.render.StorageESP;
import me.mrhakan.agalarhack.services.NotificationService;
import me.mrhakan.agalarhack.services.Waypoint;
import me.mrhakan.agalarhack.services.WaypointService;
import me.mrhakan.agalarhack.services.scanning.PointClusters;

/**
 * Reports clusters of storage as likely bases.
 *
 * <p>It draws only on evidence the client already has: the storage blocks StorageESP found in loaded
 * chunks. It opens no scan of its own, which is why it needs that module on - stated in its
 * description and its module-list label rather than left to look broken.
 *
 * <p>A cluster is evidence, not proof. The wording says "likely", because a village, a shipwreck or
 * an abandoned stash all look the same from a chest count.
 */
public class BaseFinder extends Module {
    private List<PointClusters.Cluster> clusters = List.of();
    private int cooldown;
    private int lastReported;

    public BaseFinder() {
        super("BaseFinder", Category.WORLD,
                "Reports likely bases from storage clusters StorageESP already found; enable that module too");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("clusterRadius", 12, 2, 48, "Blocks within which storage counts as the same base");
        addNumberSetting("minimumStorage", 6, 2, 64, "Storage blocks needed before a cluster is reported");
        addNumberSetting("interval", 60, 5, 600, "Ticks between re-evaluations");
        addBooleanSetting("notify", true, "Notify when a new cluster is found");
        addBooleanSetting("createWaypoint", false, "Save a waypoint at each newly reported cluster");
    }

    public List<PointClusters.Cluster> clusters() { return clusters; }

    @Override
    public void onEnable() { clusters = List.of(); cooldown = 0; lastReported = 0; }

    @Override
    public void onDisable() { clusters = List.of(); cooldown = 0; lastReported = 0; setDisplayName(null); }

    @Override public void onDisconnect() { onDisable(); }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null) return;
        var storageModule = AgalarHackClient.moduleManager.getModule("StorageESP");
        if (!(storageModule instanceof StorageESP storage) || !storage.isToggled()) {
            setDisplayName("BaseFinder [needs StorageESP]");
            clusters = List.of();
            return;
        }
        if (cooldown-- > 0) return;
        cooldown = (int) Math.round(getNumberSetting("interval", 60));

        List<PointClusters.Point> points = new ArrayList<>();
        for (var pos : storage.getCachedPositions()) {
            if (points.size() >= PointClusters.MAX_POINTS) break;
            points.add(new PointClusters.Point(pos.getX(), pos.getY(), pos.getZ()));
        }
        var found = PointClusters.group(points,
                (int) Math.round(getNumberSetting("clusterRadius", 12)),
                (int) Math.round(getNumberSetting("minimumStorage", 6)));
        report(found);
        clusters = found;
        setDisplayName(found.isEmpty() ? null : "BaseFinder [" + found.size() + "]");
    }

    /** Only reports growth, so a stable cluster does not re-notify every interval. */
    private void report(List<PointClusters.Cluster> found) {
        if (found.size() <= lastReported) {
            lastReported = found.size();
            return;
        }
        lastReported = found.size();
        var largest = found.get(0);
        if (getBooleanSetting("notify", true)) {
            service(NotificationService.class).publish(NotificationService.Type.INFO,
                    "Likely base: " + largest.size() + " storage blocks at "
                            + largest.centerX() + ", " + largest.centerY() + ", " + largest.centerZ());
        }
        if (getBooleanSetting("createWaypoint", false)) saveWaypoint(largest);
    }

    private void saveWaypoint(PointClusters.Cluster cluster) {
        var registry = me.mrhakan.agalarhack.services.ClientServices.registry();
        if (registry == null || mc.level == null) return;
        registry.find(WaypointService.class).ifPresent(waypoints -> {
            String dimension = me.mrhakan.agalarhack.module.render.Waypoints.currentDimension(mc);
            String name = "base " + cluster.centerX() + "," + cluster.centerZ();
            waypoints.add(Waypoint.of(name, cluster.centerX(), cluster.centerY(), cluster.centerZ(), dimension));
        });
    }
}
