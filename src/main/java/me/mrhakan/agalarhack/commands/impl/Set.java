package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.ChatFormatting;

public class Set extends Command {
    public Set() {
        super("set", "Changes a module setting", "set <module> <setting> <value>", "setting");
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length < 4) {
            sendUsage();
            return;
        }
        Module module = AgalarHackClient.moduleManager.getModule(args[1]);
        if (module == null) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "No module named " + ChatFormatting.WHITE + args[1] + ChatFormatting.RED + ". Use " + ChatFormatting.WHITE + AgalarHackClient.prefix + "modules" + ChatFormatting.RED + " to list them.");
            return;
        }

        String key = module.settings.getKeyIgnoreCase(args[2]);
        if (key == null || key.equals("enabled") || key.equals("keybind")) {
            StringBuilder available = new StringBuilder();
            for (String settingKey : module.settings.settings.keySet()) {
                if (settingKey.equals("enabled") || settingKey.equals("keybind")) {
                    continue;
                }
                if (available.length() > 0) {
                    available.append(ChatFormatting.GRAY).append(", ");
                }
                available.append(ChatFormatting.WHITE).append(settingKey);
            }
            if (available.length() == 0) {
                MessageManager.sendMessagePrefix(ChatFormatting.AQUA + module.getName() + ChatFormatting.RED + " has no settings.");
            } else {
                MessageManager.sendMessagePrefix(ChatFormatting.RED + "Unknown setting. " + ChatFormatting.AQUA + module.getName() + ChatFormatting.RED + " settings: " + available);
            }
            return;
        }

        Object current = module.settings.getSetting(key);
        Object newValue;
        if (current instanceof Boolean) {
            newValue = Boolean.parseBoolean(args[3]);
        } else if (current instanceof Number) {
            try {
                newValue = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                MessageManager.sendMessagePrefix(ChatFormatting.RED + "Expected a number for " + ChatFormatting.WHITE + key);
                return;
            }
        } else {
            newValue = args[3];
        }

        module.settings.setSetting(key, newValue);
        AgalarHackClient.SETTINGS_MANAGER.updateSettings();
        MessageManager.sendMessagePrefix(ChatFormatting.AQUA + module.getName() + " " + key + ChatFormatting.WHITE + " is now " + ChatFormatting.GREEN + newValue);
    }
}
