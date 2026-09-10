package me.mrhakan.agalarhack.commands.impl;

import java.util.List;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.ChatFormatting;

public class Modules extends Command {
    public Modules() {
        super("modules", "Lists modules or searches by name, category, description and settings", "modules [query]", "list", "mods");
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length > 2) {
            sendUsage();
            return;
        }

        if (args.length == 2) {
            showSearch(args[1]);
            return;
        }

        MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "Modules:");
        for (Category category : Category.values()) {
            StringBuilder line = new StringBuilder();
            for (Module module : AgalarHackClient.moduleManager.getModulesByCategory(category)) {
                if (line.length() > 0) {
                    line.append(ChatFormatting.GRAY).append(", ");
                }
                line.append(module.isToggled() ? ChatFormatting.GREEN : ChatFormatting.RED).append(module.getDisplayName());
            }
            if (line.length() > 0) {
                MessageManager.sendRawMessage(ChatFormatting.AQUA + " " + category.name + ChatFormatting.GRAY + ": " + line);
            }
        }
        MessageManager.sendRawMessage(ChatFormatting.DARK_GRAY + "  Tip: " + ChatFormatting.GRAY + AgalarHackClient.prefix
                + "modules <query>" + ChatFormatting.DARK_GRAY + " searches names, categories, descriptions and settings.");
    }

    private void showSearch(String query) {
        List<Module> matches = AgalarHackClient.moduleManager.searchModules(query);
        if (matches.isEmpty()) {
            MessageManager.sendMessagePrefix(ChatFormatting.YELLOW + "No modules matched " + ChatFormatting.WHITE + query + ChatFormatting.YELLOW + ".");
            return;
        }

        MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "Module matches for " + ChatFormatting.WHITE + query
                + ChatFormatting.GRAY + " (" + matches.size() + "): ");
        for (Module module : matches) {
            ChatFormatting state = module.isToggled() ? ChatFormatting.GREEN : ChatFormatting.RED;
            MessageManager.sendRawMessage(ChatFormatting.AQUA + "  " + module.getCategory().name + ChatFormatting.DARK_GRAY + " / "
                    + state + module.getDisplayName() + ChatFormatting.GRAY + " — " + module.getDescription());
        }
    }
}
