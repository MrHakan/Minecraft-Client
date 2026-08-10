package me.mrhakan.agalarhack.commands.impl;

import com.mojang.blaze3d.platform.InputConstants;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.ChatFormatting;

public class Bind extends Command {
    public Bind() {
        super("bind", "Binds a module to a key", "bind <module> <key|none>", "b");
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length < 3) {
            sendUsage();
            return;
        }
        Module module = AgalarHackClient.moduleManager.getModule(args[1]);
        if (module == null) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "No module named " + ChatFormatting.WHITE + args[1] + ChatFormatting.RED + ". Use " + ChatFormatting.WHITE + AgalarHackClient.prefix + "modules" + ChatFormatting.RED + " to list them.");
            return;
        }

        String keyName = args[2].toLowerCase();
        if (keyName.equals("none")) {
            module.settings.setSetting("keybind", String.valueOf(InputConstants.UNKNOWN.getValue()));
            AgalarHackClient.SETTINGS_MANAGER.updateSettings();
            MessageManager.sendMessagePrefix(ChatFormatting.AQUA + module.getName() + ChatFormatting.WHITE + " is now unbound.");
            return;
        }

        InputConstants.Key key;
        try {
            key = InputConstants.getKey("key.keyboard." + keyName);
        } catch (IllegalArgumentException e) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "Unknown key: " + ChatFormatting.WHITE + args[2] + ChatFormatting.RED + " (examples: r, g, left.shift, f4)");
            return;
        }

        if (key.getValue() == InputConstants.UNKNOWN.getValue()) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "Unknown key: " + ChatFormatting.WHITE + args[2] + ChatFormatting.RED + " (examples: r, g, left.shift, f4)");
            return;
        }

        module.settings.setSetting("keybind", String.valueOf(key.getValue()));
        AgalarHackClient.SETTINGS_MANAGER.updateSettings();
        MessageManager.sendMessagePrefix(ChatFormatting.AQUA + module.getName() + ChatFormatting.WHITE + " is now bound to " + ChatFormatting.GREEN + key.getDisplayName().getString());
    }
}
