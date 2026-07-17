package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.util.Formatting;

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
            MessageManager.sendMessagePrefix(Formatting.RED + "No module named " + Formatting.WHITE + args[1] + Formatting.RED + ". Use " + Formatting.WHITE + AgalarHackClient.prefix + "modules" + Formatting.RED + " to list them.");
            return;
        }
        module.toggle();
        if (module.isToggled()) {
            MessageManager.sendMessagePrefix(Formatting.AQUA + module.getName() + Formatting.WHITE + " is now " + Formatting.GREEN + "ON");
        } else {
            MessageManager.sendMessagePrefix(Formatting.AQUA + module.getName() + Formatting.WHITE + " is now " + Formatting.RED + "OFF");
        }
    }
}
