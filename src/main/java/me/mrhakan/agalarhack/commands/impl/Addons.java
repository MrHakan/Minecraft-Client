package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.services.AddonLoader;
import me.mrhakan.agalarhack.services.ClientServices;
import net.minecraft.ChatFormatting;

/**
 * Lists what loaded, and what did not.
 *
 * <p>An addon that failed is the case this exists for. Without it the only sign is a line in a log
 * file nobody reads, and the addon's absence looks identical to the player never having installed
 * it.
 */
public class Addons extends Command {
    public Addons() {
        super("addons", "Lists loaded addons and any that failed", "addons", "addon");
    }

    @Override
    public void onCommand(String[] args) {
        var loaded = ClientServices.require(AddonLoader.class).loaded();
        if (loaded.isEmpty()) {
            MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "No addons are installed.");
            return;
        }
        MessageManager.sendMessagePrefix(ChatFormatting.WHITE + "" + loaded.size()
                + (loaded.size() == 1 ? " addon:" : " addons:"));
        for (AddonLoader.Loaded addon : loaded) {
            if (addon.ok()) {
                MessageManager.sendMessagePrefix(ChatFormatting.GREEN + "  " + addon.name() + " "
                        + addon.version() + ChatFormatting.GRAY + " - " + addon.modules()
                        + " module(s), " + addon.commands() + " command(s)");
            } else {
                MessageManager.sendMessagePrefix(ChatFormatting.RED + "  " + addon.name() + " "
                        + addon.version() + " failed: " + ChatFormatting.GRAY + addon.failure());
            }
        }
    }
}
