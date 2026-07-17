package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.Main;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.util.text.TextFormatting;

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
        Module module = Main.moduleManager.getModule(args[1]);
        if (module == null) {
            MessageManager.sendMessagePrefix(TextFormatting.RED + "No module named " + TextFormatting.WHITE + args[1] + TextFormatting.RED + ". Use " + TextFormatting.WHITE + Main.prefix + "modules" + TextFormatting.RED + " to list them.");
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
                    available.append(TextFormatting.GRAY).append(", ");
                }
                available.append(TextFormatting.WHITE).append(settingKey);
            }
            if (available.length() == 0) {
                MessageManager.sendMessagePrefix(TextFormatting.AQUA + module.getName() + TextFormatting.RED + " has no settings.");
            } else {
                MessageManager.sendMessagePrefix(TextFormatting.RED + "Unknown setting. " + TextFormatting.AQUA + module.getName() + TextFormatting.RED + " settings: " + available);
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
                MessageManager.sendMessagePrefix(TextFormatting.RED + "Expected a number for " + TextFormatting.WHITE + key);
                return;
            }
        } else {
            newValue = args[3];
        }

        module.settings.setSetting(key, newValue);
        Main.SETTINGS_MANAGER.updateSettings();
        MessageManager.sendMessagePrefix(TextFormatting.AQUA + module.getName() + " " + key + TextFormatting.WHITE + " is now " + TextFormatting.GREEN + newValue);
    }
}
