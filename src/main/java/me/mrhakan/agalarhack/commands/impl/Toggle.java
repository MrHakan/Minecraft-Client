package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.Main;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.util.text.TextFormatting;

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
        Module module = Main.moduleManager.getModule(args[1]);
        if (module == null) {
            MessageManager.sendMessagePrefix(TextFormatting.RED + "No module named " + TextFormatting.WHITE + args[1] + TextFormatting.RED + ". Use " + TextFormatting.WHITE + Main.prefix + "modules" + TextFormatting.RED + " to list them.");
            return;
        }
        module.toggle();
        if (module.isToggled()) {
            MessageManager.sendMessagePrefix(TextFormatting.AQUA + module.getName() + TextFormatting.WHITE + " is now " + TextFormatting.GREEN + "ON");
        } else {
            MessageManager.sendMessagePrefix(TextFormatting.AQUA + module.getName() + TextFormatting.WHITE + " is now " + TextFormatting.RED + "OFF");
        }
    }
}
