package me.mrhakan.agalarhack.managers;

import java.util.ArrayList;
import java.util.List;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.commands.impl.Alias;
import me.mrhakan.agalarhack.commands.impl.MacroCommand;
import me.mrhakan.agalarhack.commands.impl.Bind;
import me.mrhakan.agalarhack.commands.impl.Friend;
import me.mrhakan.agalarhack.commands.impl.Gui;
import me.mrhakan.agalarhack.commands.impl.Help;
import me.mrhakan.agalarhack.commands.impl.ModuleSettings;
import me.mrhakan.agalarhack.commands.impl.Modules;
import me.mrhakan.agalarhack.commands.impl.Panic;
import me.mrhakan.agalarhack.commands.impl.Profile;
import me.mrhakan.agalarhack.commands.impl.Set;
import me.mrhakan.agalarhack.commands.impl.Toggle;
import me.mrhakan.agalarhack.commands.impl.PortalCommand;
import me.mrhakan.agalarhack.commands.impl.WaypointCommand;
import net.minecraft.ChatFormatting;

public class CommandManager {
    public static final List<Command> commands = new ArrayList<>();
    private static final me.mrhakan.agalarhack.services.CommandAliases ALIASES =
            new me.mrhakan.agalarhack.services.CommandAliases();

    private static final me.mrhakan.agalarhack.config.BoundedJsonFile<java.util.Map<String, String>> ALIAS_FILE =
            new me.mrhakan.agalarhack.config.BoundedJsonFile<>(
                    net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("agalarhack-aliases.json"),
                    me.mrhakan.agalarhack.config.AliasCodec.MAX_BYTES,
                    me.mrhakan.agalarhack.config.AliasCodec::decode,
                    me.mrhakan.agalarhack.config.AliasCodec::encode);

    public static me.mrhakan.agalarhack.services.CommandAliases aliases() { return ALIASES; }

    private static final me.mrhakan.agalarhack.services.MacroDefinitions MACROS =
            new me.mrhakan.agalarhack.services.MacroDefinitions();
    private static final me.mrhakan.agalarhack.config.BoundedJsonFile<java.util.List<me.mrhakan.agalarhack.services.MacroDefinitions.Macro>> MACRO_FILE =
            new me.mrhakan.agalarhack.config.BoundedJsonFile<>(
                    net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("agalarhack-macros.json"),
                    me.mrhakan.agalarhack.config.MacroCodec.MAX_BYTES,
                    me.mrhakan.agalarhack.config.MacroCodec::decode,
                    me.mrhakan.agalarhack.config.MacroCodec::encode);

    public static me.mrhakan.agalarhack.services.MacroDefinitions macros() { return MACROS; }

    public static void loadMacros() {
        try {
            MACRO_FILE.load().ifPresent(MACROS::replaceAll);
        } catch (java.io.IOException failure) {
            AgalarHackClient.LOGGER.warn("Macros preserved; saving disabled until a successful reload", failure);
        }
    }

    public static void saveMacros() {
        try {
            MACRO_FILE.save(MACROS.all());
        } catch (java.io.IOException | IllegalArgumentException failure) {
            AgalarHackClient.LOGGER.error("Could not save macros", failure);
        }
    }

    /** An unreadable alias file is preserved and saving stays off, as with every other store. */
    public static void loadAliases() {
        try {
            ALIAS_FILE.load().ifPresent(ALIASES::replaceAll);
        } catch (java.io.IOException failure) {
            AgalarHackClient.LOGGER.warn("Aliases preserved; saving disabled until a successful reload", failure);
        }
    }

    public static void saveAliases() {
        try {
            ALIAS_FILE.save(ALIASES.all());
        } catch (java.io.IOException | IllegalArgumentException failure) {
            AgalarHackClient.LOGGER.error("Could not save aliases", failure);
        }
    }

    public static void init() {
        commands.clear();
        commands.add(new Help());
        commands.add(new Modules());
        commands.add(new Toggle());
        commands.add(new Bind());
        commands.add(new Set());
        commands.add(new ModuleSettings());
        commands.add(new Friend());
        commands.add(new Profile());
        commands.add(new Gui());
        commands.add(new WaypointCommand());
        commands.add(new PortalCommand());
        loadAliases();
        loadMacros();
        commands.add(new Alias());
        commands.add(new MacroCommand());
        commands.add(new Panic());
        commands.add(new me.mrhakan.agalarhack.commands.impl.Look());
    }

    private static String firstWord(String line) {
        int space = line.indexOf(' ');
        return space < 0 ? line : line.substring(0, space);
    }

    public static Command getCommand(String label) {
        for (Command c : commands) {
            if (c.matches(label)) {
                return c;
            }
        }
        return null;
    }

    public static boolean handleChat(String message) {
        if (!message.startsWith(AgalarHackClient.prefix)) {
            return false;
        }

        String line = message.substring(AgalarHackClient.prefix.length()).trim();
        // Aliases resolve before dispatch and only once, so an alias can never shadow a real command
        // or expand into itself.
        if (getCommand(firstWord(line)) == null) {
            line = ALIASES.expand(line).orElse(line);
        }
        String[] args = line.split("\\s+");
        if (args.length == 0 || args[0].isEmpty()) {
            return true;
        }

        Command command = getCommand(args[0]);
        if (command == null) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "Unknown command. Use " + ChatFormatting.WHITE + AgalarHackClient.prefix + "help" + ChatFormatting.RED + " for a list of commands.");
            return true;
        }
        try {
            command.onCommand(args);
        } catch (Exception e) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "An error occurred while running that command.");
            me.mrhakan.agalarhack.AgalarHackClient.LOGGER.error("Operation failed", e);
        }
        return true;
    }
}
