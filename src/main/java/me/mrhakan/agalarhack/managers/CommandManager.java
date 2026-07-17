package me.mrhakan.agalarhack.managers;

import java.util.ArrayList;
import java.util.List;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.commands.impl.Bind;
import me.mrhakan.agalarhack.commands.impl.Help;
import me.mrhakan.agalarhack.commands.impl.Modules;
import me.mrhakan.agalarhack.commands.impl.Set;
import me.mrhakan.agalarhack.commands.impl.Toggle;
import net.minecraft.util.Formatting;

public class CommandManager {
    public static final List<Command> commands = new ArrayList<>();

    public static void init() {
        commands.clear();
        commands.add(new Help());
        commands.add(new Modules());
        commands.add(new Toggle());
        commands.add(new Bind());
        commands.add(new Set());
    }

    public static Command getCommand(String label) {
        for (Command c : commands) {
            if (c.matches(label)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Handles a chat message typed by the player.
     *
     * @return true if the message was a client command and should not be sent to the server
     */
    public static boolean handleChat(String message) {
        if (!message.startsWith(AgalarHackClient.prefix)) {
            return false;
        }

        String[] args = message.substring(AgalarHackClient.prefix.length()).trim().split("\\s+");
        if (args.length == 0 || args[0].isEmpty()) {
            return true;
        }

        Command command = getCommand(args[0]);
        if (command == null) {
            MessageManager.sendMessagePrefix(Formatting.RED + "Unknown command. Use " + Formatting.WHITE + AgalarHackClient.prefix + "help" + Formatting.RED + " for a list of commands.");
            return true;
        }
        try {
            command.onCommand(args);
        } catch (Exception e) {
            MessageManager.sendMessagePrefix(Formatting.RED + "An error occurred while running that command.");
            e.printStackTrace();
        }
        return true;
    }
}
