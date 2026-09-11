package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Decides what to do with the waypoint store when the player dies.
 *
 * <p>A death waypoint is only worth anything if it is still there when you get back, so the naming
 * has to survive dying again on the way. Names carry the coordinates, which makes them unique per
 * place rather than per death: dying twice at the same spot replaces one entry instead of
 * accumulating two identical ones, and dying somewhere new never overwrites the marker you are
 * currently walking towards.
 *
 * <p>The count is capped, otherwise a bad session quietly fills the store and starts pushing out
 * hand-made waypoints. Pruning is oldest-first and only ever touches other death waypoints.
 *
 * <p>Kept free of Minecraft types so the naming and pruning rules are unit tested directly.
 */
public final class DeathWaypoints {
    private DeathWaypoints() { }

    /** Marks a waypoint as ours. Also what a player would have to avoid typing to keep one safe. */
    public static final String PREFIX = "Death ";
    public static final int MAX_KEEP = 16;
    /** Red, and beamed by default: this one is meant to be found from a distance. */
    public static final int COLOR = 0xFFFF4455;

    /** @return the name a death at this position gets; deterministic, so the same spot is one entry */
    public static String nameFor(int x, int y, int z) {
        String name = PREFIX + x + " " + y + " " + z;
        return name.length() > Waypoint.MAX_NAME ? name.substring(0, Waypoint.MAX_NAME) : name;
    }

    /**
     * Recognises a waypoint this class created.
     *
     * <p>Deliberately name-based rather than a stored flag: waypoints are a persisted user-editable
     * format, and adding a field would have meant a migration for something the player can already
     * see and rename. Renaming a death waypoint therefore protects it from pruning, which is the
     * behaviour someone renaming it would expect.
     */
    public static boolean isDeathWaypoint(Waypoint waypoint) {
        return waypoint != null && waypoint.name().toLowerCase(Locale.ROOT).startsWith(PREFIX.toLowerCase(Locale.ROOT));
    }

    /**
     * What to write and what to drop.
     *
     * @param remove waypoints to delete first, oldest death waypoints beyond the cap
     * @param add the waypoint to store; replaces an existing entry with the same key
     */
    public record Plan(List<Waypoint> remove, Waypoint add) { }

    /**
     * @param existing every stored waypoint, oldest first
     * @param keep how many death waypoints to keep in total including the new one; clamped to [1, {@value #MAX_KEEP}]
     */
    public static Plan plan(List<Waypoint> existing, int x, int y, int z, String dimension, int keep) {
        Waypoint added = new Waypoint(nameFor(x, y, z), x, y, z, dimension, COLOR, true, true);
        int cap = Math.max(1, Math.min(MAX_KEEP, keep));

        List<Waypoint> deaths = new ArrayList<>();
        for (Waypoint waypoint : existing) {
            // The one being replaced is not a survivor; it is about to be overwritten in place.
            if (isDeathWaypoint(waypoint) && !waypoint.key().equals(added.key())) deaths.add(waypoint);
        }

        List<Waypoint> remove = new ArrayList<>();
        int surplus = deaths.size() - (cap - 1);
        for (int index = 0; index < surplus; index++) remove.add(deaths.get(index));
        return new Plan(List.copyOf(remove), added);
    }
}
