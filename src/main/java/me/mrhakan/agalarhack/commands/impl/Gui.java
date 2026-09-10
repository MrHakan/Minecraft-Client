package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.ui.ClickGuiScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

public class Gui extends Command {
    public Gui() {
        super("gui", "Opens the searchable module and settings interface", "gui", "clickgui");
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length != 1) {
            sendUsage();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "The ClickGUI can only be opened while in a world.");
            return;
        }
        minecraft.gui.setScreen(new ClickGuiScreen());
    }
}
