package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.managers.ModuleManager;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.util.Formatting;

public class Modules extends Command {
    public Modules() {
        super("modules", "Lists all modules and their state", "modules", "list", "mods");
    }

    @Override
    public void onCommand(String[] args) {
        MessageManager.sendMessagePrefix(Formatting.GRAY + "Modules:");
        for (Category category : Category.values()) {
            StringBuilder line = new StringBuilder();
            for (Module module : ModuleManager.getModulesByCategory(category)) {
                if (line.length() > 0) {
                    line.append(Formatting.GRAY).append(", ");
                }
                line.append(module.isToggled() ? Formatting.GREEN : Formatting.RED).append(module.getName());
            }
            if (line.length() > 0) {
                MessageManager.sendRawMessage(Formatting.AQUA + " " + category.name + Formatting.GRAY + ": " + line);
            }
        }
    }
}
