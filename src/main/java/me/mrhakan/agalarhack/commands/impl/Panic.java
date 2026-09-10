package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import net.minecraft.ChatFormatting;

public class Panic extends Command {
    public Panic() {
        super("panic", "Disables every active module at once", "panic", "disableall", "off");
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length != 1) {
            sendUsage();
            return;
        }

        int disabled = AgalarHackClient.moduleManager.disableAll();
        if (disabled == 0) {
            MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "No modules were enabled.");
            return;
        }

        MessageManager.sendMessagePrefix(ChatFormatting.GREEN + "Disabled " + disabled + " active module" + (disabled == 1 ? "." : "s."));
    }
}
