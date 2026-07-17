package me.mrhakan.agalarhack.commands;

import me.mrhakan.agalarhack.Main;
import me.mrhakan.agalarhack.managers.MessageManager;
import net.minecraft.util.text.TextFormatting;

public abstract class Command {
    private final String name;
    private final String description;
    private final String usage;
    private final String[] aliases;

    public Command(String name, String description, String usage, String... aliases) {
        this.name = name;
        this.description = description;
        this.usage = usage;
        this.aliases = aliases;
    }

    public abstract void onCommand(String[] args);

    public boolean matches(String label) {
        if (name.equalsIgnoreCase(label)) {
            return true;
        }
        for (String alias : aliases) {
            if (alias.equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }

    protected void sendUsage() {
        MessageManager.sendMessagePrefix(TextFormatting.RED + "Usage: " + TextFormatting.WHITE + Main.prefix + usage);
    }

    public String getCommand() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getUsage() {
        return usage;
    }

    public String[] getAliases() {
        return aliases;
    }
}
