package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.managers.Settings.SettingSpec;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.ChatFormatting;

public class ModuleSettings extends Command {
    public ModuleSettings() {
        super("settings", "Shows a module's settings, values and allowed ranges", "settings <module>", "cfg", "config");
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length != 2) {
            sendUsage();
            return;
        }

        Module module = AgalarHackClient.moduleManager.getModule(args[1]);
        if (module == null) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "No module named " + ChatFormatting.WHITE + args[1] + ".");
            return;
        }

        MessageManager.sendMessagePrefix(ChatFormatting.AQUA + module.getName() + ChatFormatting.GRAY + " — " + module.getDescription());
        boolean found = false;
        for (SettingSpec spec : module.settings.getSpecs()) {
            String key = spec.getName();
            if (key.equals("enabled") || key.equals("keybind")) {
                continue;
            }
            found = true;
            Object value = module.settings.getSetting(key);
            String description = spec.getDescription().isBlank() ? "" : ChatFormatting.DARK_GRAY + " — " + spec.getDescription();
            MessageManager.sendRawMessage(ChatFormatting.WHITE + "  " + key + ChatFormatting.GRAY + " = "
                    + ChatFormatting.GREEN + value + ChatFormatting.DARK_GRAY + " [" + spec.getConstraintText() + "]" + description);
        }

        if (!found) {
            MessageManager.sendRawMessage(ChatFormatting.GRAY + "  No editable settings.");
        }
    }
}
