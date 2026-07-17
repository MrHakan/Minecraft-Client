package me.mrhakan.agalarhack.managers;

import java.util.ArrayList;
import java.util.List;

import me.mrhakan.agalarhack.Main;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.commands.impl.Bind;
import me.mrhakan.agalarhack.commands.impl.Help;
import me.mrhakan.agalarhack.commands.impl.Modules;
import me.mrhakan.agalarhack.commands.impl.Set;
import me.mrhakan.agalarhack.commands.impl.Toggle;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void chatEvent(ClientChatEvent event) {
        String message = event.getMessage();
        if (!message.startsWith(Main.prefix)) {
            return;
        }
        event.setCanceled(true);
        Minecraft.getMinecraft().ingameGUI.getChatGUI().addToSentHistory(message);

        String[] args = message.substring(Main.prefix.length()).trim().split("\\s+");
        if (args.length == 0 || args[0].isEmpty()) {
            return;
        }

        Command command = getCommand(args[0]);
        if (command == null) {
            MessageManager.sendMessagePrefix(TextFormatting.RED + "Unknown command. Use " + TextFormatting.WHITE + Main.prefix + "help" + TextFormatting.RED + " for a list of commands.");
            return;
        }
        try {
            command.onCommand(args);
        } catch (Exception e) {
            MessageManager.sendMessagePrefix(TextFormatting.RED + "An error occurred while running that command.");
            e.printStackTrace();
        }
    }
}
