package me.mrhakan.agalarhack.module.render;

import java.util.List;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.Waypoint;
import me.mrhakan.agalarhack.services.WaypointService;
import net.minecraft.client.Minecraft;

/** Renders saved positions for the dimension the player is currently in. */
public class Waypoints extends Module {
    public Waypoints() {
        super("Waypoints", Category.RENDER, "Shows saved waypoints for the current dimension with optional beams and labels");
        markExperimental();
    }

    @Override
    public void selfSettings() {
        addNumberSetting("renderDistance", 512, 16, 4096, "Hide waypoints beyond this horizontal distance");
        addBooleanSetting("labels", true, "Show the waypoint name above its marker");
        addBooleanSetting("distanceInLabel", true, "Append the horizontal distance to the label");
        addBooleanSetting("beams", true, "Draw a vertical beam for waypoints that have one enabled");
        addNumberSetting("beamHeight", 256, 16, 1024, "Beam height in blocks");
        addNumberSetting("markerSize", 1.0, 0.25, 4.0, "Marker box size in blocks");
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
