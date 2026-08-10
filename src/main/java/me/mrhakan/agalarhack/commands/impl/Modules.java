package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.managers.ModuleManager;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.ChatFormatting;

public class Modules extends Command {
    public Modules() {
        super("modules", "Lists all modules and their state", "modules", "list", "mods");
    }

    @Override
    public void onCommand(String[] args) {
        MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "Modules:");
        for (Category category : Category.values()) {
            StringBuilder line = new StringBuilder();
            for (Module module : ModuleManager.getModulesByCategory(category)) {
                if (line.length() > 0) {
                    line.append(ChatFormatting.GRAY).append(", ");
                }
                line.append(module.isToggled() ? ChatFormatting.GREEN : ChatFormatting.RED).append(module.getName());
            }
            if (line.length() > 0) {
                MessageManager.sendRawMessage(ChatFormatting.AQUA + " " + category.name + ChatFormatting.GRAY + ": " + line);
            }
        }
    }
}
