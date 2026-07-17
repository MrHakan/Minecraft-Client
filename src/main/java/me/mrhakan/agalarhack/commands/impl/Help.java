package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.Main;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.CommandManager;
import me.mrhakan.agalarhack.managers.MessageManager;
import net.minecraft.util.text.TextFormatting;

public class Help extends Command {
    public Help() {
        super("help", "Shows this list of commands", "help", "h", "?");
    }

    @Override
    public void onCommand(String[] args) {
        MessageManager.sendMessagePrefix(TextFormatting.WHITE + "" + TextFormatting.BOLD + Main.name + " " + Main.currentvers + TextFormatting.RESET + TextFormatting.GRAY + " - commands:");
        for (Command command : CommandManager.commands) {
            MessageManager.sendRawMessage(TextFormatting.GRAY + " > " + TextFormatting.AQUA + Main.prefix + command.getUsage() + TextFormatting.GRAY + " - " + TextFormatting.WHITE + command.getDescription());
        }
    }
}
