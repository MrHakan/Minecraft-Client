package me.mrhakan.agalarhack.module.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    /** Which clusters have already been announced, by rough position rather than by how many there were. */
    private final Set<Long> announced = new HashSet<>();

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
    public void onEnable() { forget(); }

    @Override
    public void onDisable() { forget(); setDisplayName(null); }

    private void forget() {
        clusters = List.of();
        cooldown = 0;
        announced.clear();
    }

    @Override public void onDisconnect() { onDisable(); }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null) return;
        var storageModule = AgalarHackClient.moduleManager.getModule("StorageESP");
        if (!(storageModule instanceof StorageESP storage) || !storage.isToggled()) {
            setDisplayName("BaseFinder [needs StorageESP]");
            // Forgotten rather than just emptied: the positions came from a module that is now off,
            // so when it comes back the same bases are news again. Keeping the record meant that
            // switching StorageESP off and on left BaseFinder permanently silent about them.
            forget();
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

    /**
     * Announces clusters that have not been announced before, so a stable one does not re-notify.
     *
     * <p>This used to compare counts and then report {@code found.get(0)} - the largest cluster,
     * which by then was almost always one already known. Finding a third base told the player the
     * coordinates of the first, and with waypoints on it saved a marker there too. Clusters are
     * tracked by where they are instead, rounded to sixteen blocks so a base whose centre shifts as
     * more of it is discovered is not announced twice.
     */
    private void report(List<PointClusters.Cluster> found) {
        Set<Long> present = new HashSet<>();
        List<PointClusters.Cluster> fresh = new ArrayList<>();
        for (var cluster : found) {
            long key = positionKey(cluster);
            present.add(key);
            if (announced.add(key)) fresh.add(cluster);
        }
        // A cluster that has gone is forgotten, so it counts as news again if it comes back.
        announced.retainAll(present);
        if (fresh.isEmpty()) return;

        if (getBooleanSetting("notify", true)) {
            // `found` is largest first, so the biggest of the new ones leads.
            var lead = fresh.get(0);
            String extra = fresh.size() > 1 ? " (+" + (fresh.size() - 1) + " more)" : "";
            service(NotificationService.class).publish(NotificationService.Type.INFO,
                    "Likely base: " + lead.size() + " storage blocks at "
                            + lead.centerX() + ", " + lead.centerY() + ", " + lead.centerZ() + extra);
        }
        if (getBooleanSetting("createWaypoint", false)) {
            // "each newly reported cluster", which is what the setting says and what it now does.
            for (var cluster : fresh) saveWaypoint(cluster);
        }
    }

    private static long positionKey(PointClusters.Cluster cluster) {
        long x = Math.floorDiv(cluster.centerX(), 16);
        long z = Math.floorDiv(cluster.centerZ(), 16);
        return (x << 32) ^ (z & 0xffffffffL);
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
