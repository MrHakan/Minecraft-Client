package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.CommandManager;
import me.mrhakan.agalarhack.managers.MessageManager;
import net.minecraft.ChatFormatting;

public class Help extends Command {
    public Help() {
        super("help", "Shows this list of commands", "help", "h", "?");
    }

    @Override
    public void onCommand(String[] args) {
        MessageManager.sendMessagePrefix(ChatFormatting.WHITE + "" + ChatFormatting.BOLD + AgalarHackClient.NAME + " " + AgalarHackClient.VERSION + ChatFormatting.RESET + ChatFormatting.GRAY + " - commands:");
        for (Command command : CommandManager.commands) {
            MessageManager.sendRawMessage(ChatFormatting.GRAY + " > " + ChatFormatting.AQUA + AgalarHackClient.prefix + command.getUsage() + ChatFormatting.GRAY + " - " + ChatFormatting.WHITE + command.getDescription());
        }
    }
}
