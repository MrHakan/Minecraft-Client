package me.mrhakan.agalarhack.commands.impl;

import java.util.List;
import java.util.Locale;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

public class Profile extends Command {
    public Profile() {
        super("profile", "Manages named client profiles and per-server bindings",
                "profile <save|load|delete|list|bind|unbind|duplicate|rename|export|import> [name] [newName]", "profiles");
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length < 2) {
            sendUsage();
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        Minecraft mc = Minecraft.getInstance();
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
                    AgalarHackClient.PROFILES.bindCurrentServer(mc, args[2]);
                    ok("Bound current server to profile " + args[2]);
                }
                case "unbind" -> ok(AgalarHackClient.PROFILES.unbindCurrentServer(mc)
                        ? "Removed current server profile binding"
                        : "Current server has no profile binding");
                case "duplicate" -> {
                    requireTwoNames(args);
                    AgalarHackClient.PROFILES.duplicate(args[2], args[3]);
                    ok("Duplicated profile " + args[2] + " -> " + args[3]);
                }
                case "rename" -> {
                    requireTwoNames(args);
                    AgalarHackClient.PROFILES.rename(args[2], args[3]);
                    ok("Renamed profile " + args[2] + " -> " + args[3]);
                }
                case "export" -> {
                    requireName(args);
                    mc.keyboardHandler.setClipboard(AgalarHackClient.PROFILES.exportJson(args[2]));
                    ok("Copied profile JSON to clipboard: " + args[2]);
                }
                case "import" -> {
                    requireName(args);
                    AgalarHackClient.PROFILES.importJson(args[2], mc.keyboardHandler.getClipboard());
                    ok("Imported clipboard JSON as profile " + args[2]);
                }
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

    private void requireTwoNames(String[] args) {
        if (args.length < 4) {
            throw new IllegalArgumentException("This action requires source and target profile names.");
        }
    }

    private void ok(String text) {
        MessageManager.sendMessagePrefix(ChatFormatting.GREEN + text);
    }
}
