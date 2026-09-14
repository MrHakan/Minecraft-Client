package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.CommandManager;
import me.mrhakan.agalarhack.managers.MessageManager;
import net.minecraft.ChatFormatting;

/** {@code .alias add|remove|list} for user-defined command shorthands. */
public class Alias extends Command {
    public Alias() {
        super("alias", "Defines shorthands for longer client commands",
                "alias add <name> <command...> | remove <name> | list");
    }

    @Override
    public void onCommand(String[] args) {
        var aliases = CommandManager.aliases();
        if (args.length < 2) { sendUsage(); return; }
        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "add", "set" -> {
                if (args.length < 4) { sendUsage(); return; }
                String expansion = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
                if (CommandManager.getCommand(args[2]) != null) {
                    // Shadowing a real command would make it unreachable, so it is refused outright.
                    error("There is already a command called " + args[2] + ".");
                    return;
                }
                if (aliases.define(args[2], expansion)) {
                    ok("Alias " + args[2].toLowerCase(java.util.Locale.ROOT) + " -> " + expansion);
                    CommandManager.saveAliases();
                } else {
                    error("That alias name or command cannot be used.");
                }
            }
            case "remove", "delete", "del" -> {
                if (args.length != 3) { sendUsage(); return; }
                if (aliases.remove(args[2])) {
                    ok("Removed alias " + args[2]);
                    CommandManager.saveAliases();
                } else {
                    error("No alias named " + args[2]);
                }
            }
            case "list" -> {
                var all = aliases.all();
                if (all.isEmpty()) { MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "No aliases defined."); return; }
                MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "Aliases (" + all.size() + "):");
                all.forEach((name, expansion) -> MessageManager.sendRawMessage(
                        ChatFormatting.AQUA + " " + name + ChatFormatting.GRAY + " -> " + expansion));
            }
            default -> sendUsage();
        }
    }

    private static void ok(String message) { MessageManager.sendMessagePrefix(ChatFormatting.GREEN + message); }
    private static void error(String message) { MessageManager.sendMessagePrefix(ChatFormatting.RED + message); }
}
