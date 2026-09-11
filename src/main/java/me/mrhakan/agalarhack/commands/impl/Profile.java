package me.mrhakan.agalarhack.commands.impl;

import java.util.List;
import java.util.Locale;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.config.ProfileDiff;
import me.mrhakan.agalarhack.config.ProfileSelection;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.module.Category;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

public class Profile extends Command {
    /** Chat silently drops a long burst, so a truncated diff has to say so rather than look complete. */
    private static final int MAX_DIFF_LINES = 30;

    public Profile() {
        super("profile", "Manages named client profiles and per-server bindings",
                "profile <save|load|delete|list|bind|unbind|duplicate|rename|export|import|diff> [name] [newName|selection]", "profiles");
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
                case "load" -> loadProfile(args);
                case "diff" -> diffProfiles(args);
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

    /**
     * {@code load <name>} is unchanged; anything after the name narrows it to those parts.
     *
     * <p>A selection naming nothing real is refused rather than applied, because a partial load that
     * quietly does nothing looks identical to one that worked.
     */
    private void loadProfile(String[] args) {
        requireName(args);
        String spec = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "";
        ProfileSelection selection = ProfileSelection.parse(spec);
        if (!selection.isEverything()) {
            List<String> modules = AgalarHackClient.moduleManager.getModuleList().stream()
                    .map(module -> module.getName()).toList();
            List<String> categories = java.util.Arrays.stream(Category.values()).map(value -> value.name).toList();
            List<String> unknown = selection.unknown(modules, categories);
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("Unknown selection: " + String.join(", ", unknown)
                        + ". Use module or category names, or all/modules/hud/targets.");
            }
            if (selection.isEmpty(modules, categories)) {
                throw new IllegalArgumentException("That selection would change nothing.");
            }
        }
        String applied = AgalarHackClient.PROFILES.loadPartial(args[2], selection);
        ok("Loaded " + applied + " from profile " + args[2]);
    }

    /** {@code diff <a> [b]}; with one name the comparison is against the live configuration. */
    private void diffProfiles(String[] args) {
        requireName(args);
        String right = args.length > 3 ? args[3] : null;
        List<ProfileDiff.Change> changes = AgalarHackClient.PROFILES.diff(args[2], right);
        String against = right == null ? "current settings" : right;
        if (changes.isEmpty()) {
            ok(args[2] + " and " + against + " are identical");
            return;
        }
        MessageManager.sendMessagePrefix(ChatFormatting.AQUA + args[2] + " -> " + against + ": "
                + ChatFormatting.WHITE + ProfileDiff.summarise(changes));
        for (String line : ProfileDiff.format(changes, MAX_DIFF_LINES)) {
            MessageManager.sendRawMessage(ChatFormatting.GRAY + " " + line);
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
