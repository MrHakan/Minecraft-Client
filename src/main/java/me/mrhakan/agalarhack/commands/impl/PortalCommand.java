package me.mrhakan.agalarhack.commands.impl;

import java.util.Locale;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.module.render.Waypoints;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.PortalMath;
import me.mrhakan.agalarhack.services.Waypoint;
import me.mrhakan.agalarhack.services.WaypointService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

/**
 * {@code .portal [here|<x> <z>] [waypoint <name>]}
 *
 * <p>BlockESP can already highlight portal blocks; the question it cannot answer is where to build so
 * a pair links. This does that arithmetic, and can drop the answer straight into the waypoint store
 * so it is still there after the trip through.
 */
public class PortalCommand extends Command {
    public PortalCommand() {
        super("portal", "Converts between overworld and nether coordinates for linking portals",
                "portal [here | <x> <z>] [waypoint <name>]", "nether");
    }

    @Override
    public void onCommand(String[] args) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            error("Join a world first.");
            return;
        }
        String dimension = Waypoints.currentDimension(client);

        int x;
        int z;
        int consumed;
        if (args.length >= 3 && !"here".equalsIgnoreCase(args[1]) && !"waypoint".equalsIgnoreCase(args[1])) {
            try {
                x = Integer.parseInt(args[1]);
                z = Integer.parseInt(args[2]);
            } catch (NumberFormatException notNumbers) {
                error("Coordinates must be whole numbers.");
                return;
            }
            consumed = 3;
        } else {
            x = client.player.getBlockX();
            z = client.player.getBlockZ();
            consumed = "here".equalsIgnoreCase(args.length > 1 ? args[1] : "here") ? 2 : 1;
        }

        PortalMath.Link link = PortalMath.pair(dimension, x, z);
        if (link == null) {
            error(PortalMath.shortName(dimension) + " does not pair with another dimension by coordinate.");
            return;
        }

        MessageManager.sendMessagePrefix(ChatFormatting.AQUA + PortalMath.shortName(dimension)
                + " " + x + ", " + z + ChatFormatting.WHITE + " links to "
                + ChatFormatting.AQUA + PortalMath.shortName(link.dimension())
                + " " + link.x() + ", " + link.z());
        MessageManager.sendRawMessage(ChatFormatting.GRAY + " An existing portal within "
                + PortalMath.searchRadius(link.dimension()) + " blocks of there links instead of a new one");

        if (args.length > consumed && "waypoint".equalsIgnoreCase(args[consumed])) {
            saveWaypoint(args, consumed, link, client);
        }
    }

    /**
     * The waypoint is stored in the dimension it points at, not the one you are standing in, so it
     * is visible when you arrive rather than where it is useless.
     */
    private void saveWaypoint(String[] args, int consumed, PortalMath.Link link, Minecraft client) {
        if (args.length <= consumed + 1) {
            error("Give the waypoint a name.");
            return;
        }
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, consumed + 1, args.length));
        // Y is unknown on the other side; the player's own height is the best available guess and is
        // what vanilla uses as the starting point for its own search.
        int y = client.player.getBlockY();
        Waypoint waypoint;
        try {
            waypoint = new Waypoint(name, link.x(), y, link.z(), link.dimension(), Waypoint.DEFAULT_COLOR, true, true);
        } catch (IllegalArgumentException badName) {
            error(badName.getMessage());
            return;
        }
        // Through the shared rule rather than its own green line. This is the second place that
        // claimed a waypoint was stored, and the write is refused here exactly as it is there, so
        // fixing only `.waypoint` would have left `.portal` still promising a durable save.
        WaypointService service = ClientServices.require(WaypointService.class);
        if (service.add(waypoint)) {
            MessageManager.sendMessagePrefix((service.hasUnsavedChanges() ? ChatFormatting.YELLOW : ChatFormatting.GREEN)
                    + WaypointCommand.persistenceFeedback(service, "Saved waypoint " + waypoint.name()
                            + " in the " + PortalMath.shortName(link.dimension())));
        } else {
            error("The waypoint store is full.");
        }
    }

    private void error(String text) {
        MessageManager.sendMessagePrefix(ChatFormatting.RED + text);
    }
}
