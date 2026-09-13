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
        // Deliberately the any-dimension lookup: removing "home" by name should work from the nether.
        Optional<Waypoint> match = find(name, dimension).or(() -> findAnywhere(name));
        if (match.isEmpty()) return false;
        waypoints.remove(match.get().key());
        save();
        return true;
    }

    /**
     * The waypoint of that name <strong>in that dimension</strong>, and nowhere else.
     *
     * <p>This used to fall back to any dimension when the name was not found locally, which suits
     * removing something by name from wherever you happen to be and suits nothing else. Coordinates
     * do not carry across: `.goto base` in the nether found the overworld's "base" and pathed to its
     * raw numbers, eight times further out than the player meant, under a message that said "in this
     * dimension". Editing a waypoint's colour or visibility reached across the same way.
     *
     * <p>{@link #findAnywhere} is that fallback, for the callers that actually want it.
     */
    public Optional<Waypoint> find(String name, String dimension) {
        if (name == null || name.isBlank() || dimension == null) return Optional.empty();
        Waypoint exact = waypoints.get(dimension + "/" + name.trim().toLowerCase(Locale.ROOT));
        return Optional.ofNullable(exact);
    }

    /** The first waypoint of that name in any dimension, so removing one by name works from anywhere. */
    public Optional<Waypoint> findAnywhere(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String wanted = name.trim().toLowerCase(Locale.ROOT);
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

    /**
     * Removes every waypoint in one dimension. A null dimension removes <strong>nothing</strong>.
     *
     * <p>Null used to mean "every dimension", which is a dangerous thing for a missing value to
     * mean. `.waypoint clear` passes the dimension the player is standing in, and that is null when
     * there is no level - so the command that clears one dimension, behind no confirmation, could
     * delete every waypoint the player had and then report it as "in this dimension". Wiping
     * everything is {@link #clearAll}, which a caller has to ask for by name.
     */
    public int clear(String dimension) {
        if (dimension == null) return 0;
        int before = waypoints.size();
        waypoints.values().removeIf(point -> point.dimension().equals(dimension));
        if (waypoints.size() != before) save();
        return before - waypoints.size();
    }

    /** Removes every waypoint in every dimension. Guarded by an explicit confirmation at the command. */
    public int clearAll() {
        int before = waypoints.size();
        waypoints.clear();
        if (before != 0) save();
        return before;
    }
}
