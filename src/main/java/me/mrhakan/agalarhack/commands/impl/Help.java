package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.CommandManager;
import me.mrhakan.agalarhack.managers.MessageManager;
import net.minecraft.util.Formatting;

public class Help extends Command {
    public Help() {
        super("help", "Shows this list of commands", "help", "h", "?");
    }

    @Override
    public void onCommand(String[] args) {
        MessageManager.sendMessagePrefix(Formatting.WHITE + "" + Formatting.BOLD + AgalarHackClient.NAME + " " + AgalarHackClient.VERSION + Formatting.RESET + Formatting.GRAY + " - commands:");
        for (Command command : CommandManager.commands) {
            MessageManager.sendRawMessage(Formatting.GRAY + " > " + Formatting.AQUA + AgalarHackClient.prefix + command.getUsage() + Formatting.GRAY + " - " + Formatting.WHITE + command.getDescription());
        }
    }
}
