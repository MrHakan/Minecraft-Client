package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.managers.ModuleManager;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.util.text.TextFormatting;

public class Modules extends Command {
    public Modules() {
        super("modules", "Lists all modules and their state", "modules", "list", "mods");
    }

    @Override
    public void onCommand(String[] args) {
        MessageManager.sendMessagePrefix(TextFormatting.GRAY + "Modules:");
        for (Category category : Category.values()) {
            StringBuilder line = new StringBuilder();
            for (Module module : ModuleManager.getModulesByCategory(category)) {
                if (line.length() > 0) {
                    line.append(TextFormatting.GRAY).append(", ");
                }
                line.append(module.isToggled() ? TextFormatting.GREEN : TextFormatting.RED).append(module.getName());
            }
            if (line.length() > 0) {
                MessageManager.sendRawMessage(TextFormatting.AQUA + " " + category.name + TextFormatting.GRAY + ": " + line);
            }
        }
    }
}
