package me.mrhakan.agalarhack.commands.impl;

import java.util.List;
import java.util.Locale;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.module.render.Waypoints;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.Waypoint;
import me.mrhakan.agalarhack.services.WaypointService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

/** {@code .waypoint add|remove|list|clear|beam|hide|show|color} */
public class WaypointCommand extends Command {
    public WaypointCommand() {
        super("waypoint", "Saves and manages named positions",
                "waypoint add <name> [x y z] | remove <name> | list | clear [all] | beam <name> | show <name> | hide <name> | color <name> <rrggbb>",
                "wp");
    }

    @Override
    public void onCommand(String[] args) {
        Minecraft client = Minecraft.getInstance();
        WaypointService service = ClientServices.require(WaypointService.class);
        if (args.length < 2) { sendUsage(); return; }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> add(client, service, args);
            case "remove", "delete", "del" -> remove(client, service, args);
            case "list" -> list(client, service);
            case "clear" -> clear(client, service, args);
            case "beam" -> toggle(client, service, args, "beam");
            case "show" -> toggle(client, service, args, "show");
            case "hide" -> toggle(client, service, args, "hide");
            case "color", "colour" -> color(client, service, args);
            default -> sendUsage();
        }
    }

    private void add(Minecraft client, WaypointService service, String[] args) {
        if (client.player == null || client.level == null) {
            error("Join a world first.");
            return;
        }
        if (args.length != 3 && args.length != 6) { sendUsage(); return; }
        String dimension = Waypoints.currentDimension(client);
        int x;
        int y;
        int z;
        if (args.length == 6) {
            try {
                x = Integer.parseInt(args[3]);
                y = Integer.parseInt(args[4]);
                z = Integer.parseInt(args[5]);
            } catch (NumberFormatException notNumbers) {
                error("Coordinates must be whole numbers.");
                return;
            }
        } else {
            x = client.player.getBlockX();
            y = client.player.getBlockY();
            z = client.player.getBlockZ();
        }
        Waypoint waypoint;
        try {
            waypoint = Waypoint.of(args[2], x, y, z, dimension);
        } catch (IllegalArgumentException invalid) {
            error("That name cannot be used.");
            return;
        }
        boolean replacing = service.find(waypoint.name(), dimension).isPresent();
        if (!service.add(waypoint)) {
            error("Waypoint limit reached; remove one first.");
            return;
        }
        ok((replacing ? "Updated " : "Saved ") + waypoint.name() + " at "
                + waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z());
    }

    private void remove(Minecraft client, WaypointService service, String[] args) {
        if (args.length != 3) { sendUsage(); return; }
        if (service.remove(args[2], Waypoints.currentDimension(client))) ok("Removed " + args[2]);
        else error("No waypoint named " + args[2]);
    }

    private void list(Minecraft client, WaypointService service) {
        List<Waypoint> all = service.all();
        if (all.isEmpty()) { MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "No waypoints saved."); return; }
        String dimension = Waypoints.currentDimension(client);
        MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "Waypoints (" + all.size() + "):");
        for (Waypoint point : all) {
            boolean here = point.dimension().equals(dimension);
            String distance = here && client.player != null
                    ? " " + ChatFormatting.DARK_GRAY + Math.round(point.horizontalDistanceTo(client.player.getX(), client.player.getZ())) + "m"
                    : "";
            MessageManager.sendRawMessage((point.visible() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY) + " " + point.name()
                    + ChatFormatting.GRAY + " " + point.x() + ", " + point.y() + ", " + point.z()
                    + (here ? "" : ChatFormatting.DARK_GRAY + " [" + shortDimension(point.dimension()) + "]") + distance);
        }
    }

    private void clear(Minecraft client, WaypointService service, String[] args) {
        boolean everywhere = args.length == 3 && args[2].equalsIgnoreCase("all");
        if (args.length > 3 || (args.length == 3 && !everywhere)) { sendUsage(); return; }
        int removed = service.clear(everywhere ? null : Waypoints.currentDimension(client));
        ok("Removed " + removed + (everywhere ? " waypoints." : " waypoints in this dimension."));
    }

    private void toggle(Minecraft client, WaypointService service, String[] args, String action) {
        if (args.length != 3) { sendUsage(); return; }
        var found = service.find(args[2], Waypoints.currentDimension(client));
        if (found.isEmpty()) { error("No waypoint named " + args[2]); return; }
        Waypoint point = found.get();
        Waypoint updated = switch (action) {
            case "beam" -> point.withBeam(!point.beam());
            case "show" -> point.withVisible(true);
            default -> point.withVisible(false);
        };
        service.replace(updated);
        ok(point.name() + ": " + (action.equals("beam") ? "beam " + (updated.beam() ? "on" : "off")
                : updated.visible() ? "shown" : "hidden"));
    }

    private void color(Minecraft client, WaypointService service, String[] args) {
        if (args.length != 4) { sendUsage(); return; }
        var found = service.find(args[2], Waypoints.currentDimension(client));
        if (found.isEmpty()) { error("No waypoint named " + args[2]); return; }
        String hex = args[3].startsWith("#") ? args[3].substring(1) : args[3];
        if (hex.length() != 6) { error("Colour must be six hex digits, for example ff8800."); return; }
        int rgb;
        try {
            rgb = Integer.parseInt(hex, 16);
        } catch (NumberFormatException invalid) {
            error("Colour must be six hex digits, for example ff8800.");
            return;
        }
        service.replace(found.get().withColor(rgb));
        ok(found.get().name() + " colour set.");
    }

    private static String shortDimension(String dimension) {
        int separator = dimension.indexOf(':');
        return separator < 0 ? dimension : dimension.substring(separator + 1);
    }

    private static void ok(String message) { MessageManager.sendMessagePrefix(ChatFormatting.GREEN + message); }
    private static void error(String message) { MessageManager.sendMessagePrefix(ChatFormatting.RED + message); }
}
