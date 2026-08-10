package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.ChatFormatting;

public class Toggle extends Command {
    public Toggle() {
        super("toggle", "Toggles a module on or off", "toggle <module>", "t");
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length < 2) {
            sendUsage();
            return;
        }
        Module module = AgalarHackClient.moduleManager.getModule(args[1]);
        if (module == null) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "No module named " + ChatFormatting.WHITE + args[1] + ChatFormatting.RED + ". Use " + ChatFormatting.WHITE + AgalarHackClient.prefix + "modules" + ChatFormatting.RED + " to list them.");
            return;
        }
        module.toggle();
        if (module.isToggled()) {
            MessageManager.sendMessagePrefix(ChatFormatting.AQUA + module.getName() + ChatFormatting.WHITE + " is now " + ChatFormatting.GREEN + "ON");
        } else {
            MessageManager.sendMessagePrefix(ChatFormatting.AQUA + module.getName() + ChatFormatting.WHITE + " is now " + ChatFormatting.RED + "OFF");
        }
    }
}
