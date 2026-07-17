package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.util.Formatting;

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
            MessageManager.sendMessagePrefix(Formatting.RED + "No module named " + Formatting.WHITE + args[1] + Formatting.RED + ". Use " + Formatting.WHITE + AgalarHackClient.prefix + "modules" + Formatting.RED + " to list them.");
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
                    available.append(Formatting.GRAY).append(", ");
                }
                available.append(Formatting.WHITE).append(settingKey);
            }
            if (available.length() == 0) {
                MessageManager.sendMessagePrefix(Formatting.AQUA + module.getName() + Formatting.RED + " has no settings.");
            } else {
                MessageManager.sendMessagePrefix(Formatting.RED + "Unknown setting. " + Formatting.AQUA + module.getName() + Formatting.RED + " settings: " + available);
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
                MessageManager.sendMessagePrefix(Formatting.RED + "Expected a number for " + Formatting.WHITE + key);
                return;
            }
        } else {
            newValue = args[3];
        }

        module.settings.setSetting(key, newValue);
        AgalarHackClient.SETTINGS_MANAGER.updateSettings();
        MessageManager.sendMessagePrefix(Formatting.AQUA + module.getName() + " " + key + Formatting.WHITE + " is now " + Formatting.GREEN + newValue);
    }
}
