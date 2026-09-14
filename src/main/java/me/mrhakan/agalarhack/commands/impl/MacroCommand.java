package me.mrhakan.agalarhack.commands.impl;

import java.util.Arrays;
import java.util.Locale;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.input.KeyChord;
import me.mrhakan.agalarhack.managers.CommandManager;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.services.MacroDefinitions;
import net.minecraft.ChatFormatting;

/** {@code .macro add|remove|list} binding a key to one chat line, command or module toggle. */
public class MacroCommand extends Command {
    public MacroCommand() {
        super("macro", "Binds a key to a chat line, a command or a module toggle",
                "macro add <key> <chat|command|toggle> <action...> | remove <key> | list");
    }

    @Override
    public void onCommand(String[] args) {
        var macros = CommandManager.macros();
        if (args.length < 2) { sendUsage(); return; }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add", "set" -> add(macros, args);
            case "remove", "delete", "del" -> remove(macros, args);
            case "list" -> list(macros);
            default -> sendUsage();
        }
    }

    private void add(MacroDefinitions macros, String[] args) {
        if (args.length < 5) { sendUsage(); return; }
        int key = KeyChord.keyFromName(args[2]);
        if (key == -1) {
            error("Unknown key: " + args[2] + ". Use a name such as G, F7 or NUMPAD_1.");
            return;
        }
        var kind = MacroDefinitions.parseKind(args[3]);
        if (kind == null) { error("Type must be chat, command or toggle."); return; }
        String action = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        if (!macros.define(key, false, kind, action)) {
            error("That macro could not be stored; check the action length and the macro limit.");
            return;
        }
        CommandManager.saveMacros();
        ok("Macro on " + args[2].toUpperCase(Locale.ROOT) + ": " + kind.name().toLowerCase(Locale.ROOT) + " " + action);
    }

    private void remove(MacroDefinitions macros, String[] args) {
        if (args.length != 3) { sendUsage(); return; }
        int key = KeyChord.keyFromName(args[2]);
        if (key == -1 || !macros.remove(key, false)) { error("No macro on " + args[2]); return; }
        CommandManager.saveMacros();
        ok("Removed macro on " + args[2].toUpperCase(Locale.ROOT));
    }

    private void list(MacroDefinitions macros) {
        var all = macros.all();
        if (all.isEmpty()) { MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "No macros defined."); return; }
        MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "Macros (" + all.size() + "):");
        for (var macro : all) {
            MessageManager.sendRawMessage(ChatFormatting.AQUA + " " + KeyChord.nameOf(macro.key(), macro.mouse())
                    + ChatFormatting.GRAY + " -> " + macro.kind().name().toLowerCase(Locale.ROOT) + " " + macro.action());
        }
    }

    private static void ok(String message) { MessageManager.sendMessagePrefix(ChatFormatting.GREEN + message); }
    private static void error(String message) { MessageManager.sendMessagePrefix(ChatFormatting.RED + message); }
}
