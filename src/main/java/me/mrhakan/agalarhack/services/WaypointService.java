package me.mrhakan.agalarhack.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import me.mrhakan.agalarhack.config.BoundedJsonFile;
import me.mrhakan.agalarhack.config.WaypointCodec;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistent named positions.
 *
 * <p>Waypoints are player data, so the store follows the same rule as the other config files: an
 * unreadable file is preserved and saving is disabled until a successful reload, rather than being
 * silently replaced with an empty list.
 */
public final class WaypointService {
    /**
     * Its own logger rather than the client's shared one: that field lives on the composition root,
     * whose static initialiser needs a Fabric runtime, which would make this store's failure paths
     * impossible to test.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(WaypointService.class);

    private final Map<String, Waypoint> waypoints = new LinkedHashMap<>();
    private final BoundedJsonFile<Map<String, Waypoint>> file;
    private boolean loaded;

    public WaypointService() {
        this(FabricLoader.getInstance().getConfigDir().resolve("agalarhack-waypoints.json"));
    }

    WaypointService(java.nio.file.Path path) {
        file = new BoundedJsonFile<>(path, WaypointCodec.MAX_BYTES, WaypointCodec::decode, WaypointCodec::encode);
    }

    public void load() {
        try {
            file.load().ifPresent(stored -> { waypoints.clear(); waypoints.putAll(stored); });
            loaded = true;
        } catch (IOException failure) {
            loaded = false;
            LOGGER.warn("Waypoints preserved; saving disabled until a successful reload", failure);
            notify(NotificationService.Type.WARNING, "Unreadable waypoint file retained");
        }
    }

    /** The registry is absent in tests and before the client finishes starting; both are fine. */
    private static void notify(NotificationService.Type type, String message) {
        var registry = ClientServices.registry();
        if (registry == null) return;
        registry.find(NotificationService.class).ifPresent(notifications -> notifications.publish(type, message));
    }

    public boolean save() {
        if (!loaded) return false;
        try {
            file.save(waypoints);
            return true;
        } catch (IOException | IllegalArgumentException failure) {
            LOGGER.error("Could not save waypoints", failure);
            return false;
        }
    }

    /** @return false when the store is full; an existing waypoint with the same key is replaced */
    public boolean add(Waypoint waypoint) {
        if (waypoint == null) return false;
        if (!waypoints.containsKey(waypoint.key()) && waypoints.size() >= WaypointCodec.MAX_WAYPOINTS) return false;
        waypoints.put(waypoint.key(), waypoint);
        save();
        return true;
    }

    public boolean remove(String name, String dimension) {
        Optional<Waypoint> match = find(name, dimension);
        if (match.isEmpty()) return false;
        waypoints.remove(match.get().key());
        save();
        return true;
    }

    /** Looks in the given dimension first, then anywhere, so ".waypoint remove home" works from the nether. */
    public Optional<Waypoint> find(String name, String dimension) {
        if (name == null || name.isBlank()) return Optional.empty();
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        if (dimension != null) {
            Waypoint exact = waypoints.get(dimension + "/" + wanted);
            if (exact != null) return Optional.of(exact);
        }
        return waypoints.values().stream()
                .filter(point -> point.name().toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst();
    }

    public List<Waypoint> all() { return List.copyOf(waypoints.values()); }

    /** Visible waypoints in one dimension, which is what rendering and the HUD arrow consume. */
    public List<Waypoint> visibleIn(String dimension) {
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint point : waypoints.values()) {
            if (point.visible() && point.dimension().equals(dimension)) result.add(point);
        }
        return List.copyOf(result);
    }

    public int count() { return waypoints.size(); }

    public boolean replace(Waypoint waypoint) {
        if (waypoint == null || !waypoints.containsKey(waypoint.key())) return false;
        waypoints.put(waypoint.key(), waypoint);
        save();
        return true;
    }

    /**
     * Removes several waypoints and stores one, writing the file once.
     *
     * <p>{@link #add} and {@link #remove} each save, so applying a prune plan through them would
     * rewrite the file up to {@value me.mrhakan.agalarhack.services.DeathWaypoints#MAX_KEEP} times
     * for a single death. Callers that already know the whole change use this instead.
     *
     * @return false when the store is full and the addition could not be made; removals still applied
     */
    public boolean apply(List<Waypoint> remove, Waypoint add) {
        if (remove != null) {
            for (Waypoint waypoint : remove) {
                if (waypoint != null) waypoints.remove(waypoint.key());
            }
        }
        boolean added = true;
        if (add != null) {
            if (!waypoints.containsKey(add.key()) && waypoints.size() >= WaypointCodec.MAX_WAYPOINTS) added = false;
            else waypoints.put(add.key(), add);
        }
        save();
        return added;
    }

    public int clear(String dimension) {
        int before = waypoints.size();
        waypoints.values().removeIf(point -> dimension == null || point.dimension().equals(dimension));
        if (waypoints.size() != before) save();
        return before - waypoints.size();
    }
}
