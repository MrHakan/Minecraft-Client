package me.mrhakan.agalarhack.commands.impl;

import org.lwjgl.input.Keyboard;

import me.mrhakan.agalarhack.Main;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.util.text.TextFormatting;

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
        Module module = Main.moduleManager.getModule(args[1]);
        if (module == null) {
            MessageManager.sendMessagePrefix(TextFormatting.RED + "No module named " + TextFormatting.WHITE + args[1] + TextFormatting.RED + ". Use " + TextFormatting.WHITE + Main.prefix + "modules" + TextFormatting.RED + " to list them.");
            return;
        }

        String keyName = args[2].toUpperCase();
        if (keyName.equals("NONE")) {
            module.settings.setSetting("keybind", String.valueOf(Keyboard.KEY_NONE));
            Main.SETTINGS_MANAGER.updateSettings();
            MessageManager.sendMessagePrefix(TextFormatting.AQUA + module.getName() + TextFormatting.WHITE + " is now unbound.");
            return;
        }

        int key = Keyboard.getKeyIndex(keyName);
        if (key == Keyboard.KEY_NONE) {
            MessageManager.sendMessagePrefix(TextFormatting.RED + "Unknown key: " + TextFormatting.WHITE + keyName);
            return;
        }

        module.settings.setSetting("keybind", String.valueOf(key));
        Main.SETTINGS_MANAGER.updateSettings();
        MessageManager.sendMessagePrefix(TextFormatting.AQUA + module.getName() + TextFormatting.WHITE + " is now bound to " + TextFormatting.GREEN + keyName);
    }
}
