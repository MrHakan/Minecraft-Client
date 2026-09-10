package me.mrhakan.agalarhack.commands.impl;

import java.util.List;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

public class Profile extends Command {
    public Profile() {
        super("profile", "Manages named client profiles and per-server bindings",
                "profile <save|load|delete|list|bind|unbind> [name]", "profiles");
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length < 2) {
            sendUsage();
            return;
        }
        String action = args[1].toLowerCase();
        try {
            switch (action) {
                case "list" -> listProfiles();
                case "save" -> {
                    requireName(args);
                    AgalarHackClient.PROFILES.save(args[2]);
                    ok("Saved profile " + args[2]);
                }
                case "load" -> {
                    requireName(args);
                    AgalarHackClient.PROFILES.load(args[2]);
                    ok("Loaded profile " + args[2]);
                }
                case "delete" -> {
                    requireName(args);
                    ok(AgalarHackClient.PROFILES.delete(args[2])
                            ? "Deleted profile " + args[2]
                            : "Profile not found: " + args[2]);
                }
                case "bind" -> {
                    requireName(args);
                    AgalarHackClient.PROFILES.bindCurrentServer(Minecraft.getInstance(), args[2]);
                    ok("Bound current server to profile " + args[2]);
                }
                case "unbind" -> ok(AgalarHackClient.PROFILES.unbindCurrentServer(Minecraft.getInstance())
                        ? "Removed current server profile binding"
                        : "Current server has no profile binding");
                default -> sendUsage();
            }
        } catch (RuntimeException e) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + e.getMessage());
        }
    }

    private void listProfiles() {
        List<String> profiles = AgalarHackClient.PROFILES.list();
        String active = AgalarHackClient.PROFILES.getActiveProfile();
        String bound = AgalarHackClient.PROFILES.getBoundProfile(Minecraft.getInstance());
        MessageManager.sendMessagePrefix(ChatFormatting.AQUA + "Profiles: "
                + ChatFormatting.WHITE + (profiles.isEmpty() ? "none" : String.join(", ", profiles)));
        MessageManager.sendRawMessage(ChatFormatting.GRAY + " Active: " + (active.isBlank() ? "none" : active)
                + " | Current server: " + (bound == null ? "none" : bound));
    }

    private void requireName(String[] args) {
        if (args.length < 3) {
            throw new IllegalArgumentException("This action requires a profile name.");
        }
    }

    private void ok(String text) {
        MessageManager.sendMessagePrefix(ChatFormatting.GREEN + text);
    }
}
