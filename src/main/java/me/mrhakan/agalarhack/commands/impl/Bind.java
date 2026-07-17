package me.mrhakan.agalarhack.commands.impl;

import org.lwjgl.glfw.GLFW;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Formatting;

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
            MessageManager.sendMessagePrefix(Formatting.RED + "No module named " + Formatting.WHITE + args[1] + Formatting.RED + ". Use " + Formatting.WHITE + AgalarHackClient.prefix + "modules" + Formatting.RED + " to list them.");
            return;
        }

        String keyName = args[2].toLowerCase();
        if (keyName.equals("none")) {
            module.settings.setSetting("keybind", String.valueOf(GLFW.GLFW_KEY_UNKNOWN));
            AgalarHackClient.SETTINGS_MANAGER.updateSettings();
            MessageManager.sendMessagePrefix(Formatting.AQUA + module.getName() + Formatting.WHITE + " is now unbound.");
            return;
        }

        InputUtil.Key key;
        try {
            key = InputUtil.fromTranslationKey("key.keyboard." + keyName);
        } catch (IllegalArgumentException e) {
            MessageManager.sendMessagePrefix(Formatting.RED + "Unknown key: " + Formatting.WHITE + args[2] + Formatting.RED + " (examples: r, g, left.shift, f4)");
            return;
        }

        module.settings.setSetting("keybind", String.valueOf(key.getCode()));
        AgalarHackClient.SETTINGS_MANAGER.updateSettings();
        MessageManager.sendMessagePrefix(Formatting.AQUA + module.getName() + Formatting.WHITE + " is now bound to " + Formatting.GREEN + key.getLocalizedText().getString());
    }
}
