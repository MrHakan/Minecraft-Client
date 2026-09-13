package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.services.BaritoneBridge;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.Waypoint;
import me.mrhakan.agalarhack.services.WaypointService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

/**
 * Sends Baritone to a position, a waypoint, or stops it.
 *
 * <p>Every path out of here is a Java call into Baritone's API. Nothing is ever put in the chat box:
 * the shortcut of sending {@code #goto 100 64 -200} as a message is how a player without Baritone
 * installed, or with its prefix turned off, broadcasts their base coordinates to the server. That is
 * not a risk worth a few lines saved, so the bridge has no chat path at all.
 *
 * <p>When Baritone is not installed this says so once, plainly, and does nothing else.
 */
public class Goto extends Command {
    public Goto() {
        super("goto", "Sends Baritone to a position or a waypoint; needs Baritone installed",
                "goto <x> <z> | goto <x> <y> <z> | goto <waypoint> | goto stop", "path", "baritone");
    }

    @Override
    public void onCommand(String[] args) {
        BaritoneBridge baritone = ClientServices.require(BaritoneBridge.class);
        if (args.length < 2) { sendUsage(); return; }

        if (args[1].equalsIgnoreCase("stop") || args[1].equalsIgnoreCase("cancel")) {
            announce(baritone.cancel(), "Stopped Baritone.");
            return;
        }

        if (args.length == 3) {
            Integer x = whole(args[1]), z = whole(args[2]);
            if (x == null || z == null) { sendUsage(); return; }
            announce(baritone.pathTo(x, z), "Pathing to " + x + ", " + z + ".");
            return;
        }
        if (args.length == 4) {
            Integer x = whole(args[1]), y = whole(args[2]), z = whole(args[3]);
            if (x == null || y == null || z == null) { sendUsage(); return; }
            announce(baritone.pathTo(x, y, z), "Pathing to " + x + ", " + y + ", " + z + ".");
            return;
        }
        if (args.length != 2) { sendUsage(); return; }

        // A name: the waypoints this client already keeps are the obvious thing to path to.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "You need to be in a world.");
            return;
        }
        String dimension = me.mrhakan.agalarhack.module.render.Waypoints.currentDimension(mc);
        var found = ClientServices.require(WaypointService.class).find(args[1], dimension);
        if (found.isEmpty()) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "No waypoint called "
                    + ChatFormatting.WHITE + args[1] + ChatFormatting.RED + " in this dimension.");
            return;
        }
        Waypoint waypoint = found.get();
        announce(baritone.pathTo(waypoint.x(), waypoint.y(), waypoint.z()),
                "Pathing to " + waypoint.name() + ".");
    }

    /**
     * One place decides what the player is told, so "Baritone is not installed" can never be
     * confused with "Baritone refused" — and neither is ever a silent no-op.
     */
    private static void announce(BaritoneBridge.Result result, String success) {
        switch (result) {
            case STARTED -> MessageManager.sendMessagePrefix(ChatFormatting.GREEN + success);
            case ABSENT -> MessageManager.sendMessagePrefix(ChatFormatting.RED
                    + "Baritone is not installed, so there is nothing to path with. "
                    + "Nothing was sent to chat.");
            case FAILED -> MessageManager.sendMessagePrefix(ChatFormatting.RED
                    + "Baritone is installed but refused that; see the log.");
        }
    }

    private static Integer whole(String raw) {
        try {
            return Integer.valueOf((int) Math.floor(Double.parseDouble(raw)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
