package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.managers.Settings.SettingSpec;
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
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "No module named " + ChatFormatting.WHITE + args[1]
                    + ChatFormatting.RED + ". Use " + ChatFormatting.WHITE + AgalarHackClient.prefix + "modules"
                    + ChatFormatting.RED + " to list them.");
            return;
        }

        String key = module.settings.getKeyIgnoreCase(args[2]);
        SettingSpec spec = key == null ? null : module.settings.getSpecIgnoreCase(key);
        if (key == null || spec == null || key.equals("enabled") || key.equals("keybind")) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "Unknown or read-only setting. Use "
                    + ChatFormatting.WHITE + AgalarHackClient.prefix + "settings " + module.getName()
                    + ChatFormatting.RED + " to inspect editable settings.");
            return;
        }

        final Object newValue;
        try {
            newValue = module.settings.parseSettingValue(key, args[3]);
        } catch (IllegalArgumentException e) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + e.getMessage() + ChatFormatting.GRAY
                    + " Allowed: " + ChatFormatting.WHITE + spec.getConstraintText());
            return;
        }

        module.settings.setSetting(key, newValue);
        AgalarHackClient.SETTINGS_MANAGER.updateSettings();
        MessageManager.sendMessagePrefix(ChatFormatting.AQUA + module.getName() + " " + key
                + ChatFormatting.WHITE + " is now " + ChatFormatting.GREEN + newValue);
    }
}
