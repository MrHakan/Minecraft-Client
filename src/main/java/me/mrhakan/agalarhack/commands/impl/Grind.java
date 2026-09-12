package me.mrhakan.agalarhack.commands.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.services.CraftingPlan;
import me.mrhakan.agalarhack.services.GrindBook;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

/**
 * Works out what it would take to obtain something, counting what the player already has.
 *
 * <p><strong>This plans; it does not yet do.</strong> Saying so in the command itself is the point:
 * the planner is finished and tested, the part that walks to a tree and swings at it is not, and a
 * command that quietly did half of a grind would be worse than one that does none of it. Every
 * reply here is about what a run <em>would</em> need.
 *
 * <p>The counting is the half that is easy to get wrong and easy to check: birch planks count
 * towards planks, charcoal towards coal, and anything already in the bag comes off the list, so
 * asking twice in a row gives a shorter answer the second time.
 */
public class Grind extends Command {

    /** Kept small on purpose: a chat reply that scrolls the log away helps nobody. */
    private static final int MAX_LINES = 12;

    public Grind() {
        super("grind", "Plans what it would take to obtain an item",
                "grind <item> [count]", "plan");
    }

    @Override
    public void onCommand(String[] args) {
        // args[0] is the command name itself; the dispatcher passes the whole split line.
        if (args.length < 2 || args[1].isBlank()) {
            sendUsage();
            known();
            return;
        }
        String target = GrindBook.generic(args[1]);
        if (!GrindBook.knows(target)) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "Nothing in the book for \""
                    + args[1] + "\".");
            known();
            return;
        }
        int wanted = 1;
        if (args.length > 2) {
            try {
                wanted = Integer.parseInt(args[2]);
            } catch (NumberFormatException notANumber) {
                MessageManager.sendMessagePrefix(ChatFormatting.RED + "\"" + args[2]
                        + "\" is not a number.");
                return;
            }
            if (wanted < 1 || wanted > 512) {
                MessageManager.sendMessagePrefix(ChatFormatting.RED + "Ask for between 1 and 512.");
                return;
            }
        }

        Map<String, Integer> have = carried(Minecraft.getInstance());
        List<CraftingPlan.Step> steps;
        try {
            steps = CraftingPlan.plan(target, wanted, have, GrindBook.recipes());
        } catch (RuntimeException broken) {
            // A cycle or an over-deep chain is a bug in the book rather than in the player's request.
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "The recipe book is inconsistent: "
                    + broken.getMessage());
            AgalarHackClient.LOGGER.error("Grind planning failed for {} x{}", target, wanted, broken);
            return;
        }

        if (steps.isEmpty()) {
            MessageManager.sendMessagePrefix(ChatFormatting.GREEN + "You already have "
                    + wanted + " " + target + ".");
            return;
        }

        MessageManager.sendMessagePrefix(ChatFormatting.WHITE + "To get " + wanted + " " + target
                + ChatFormatting.GRAY + " (" + steps.size()
                + (steps.size() == 1 ? " step" : " steps") + "):");
        for (int i = 0; i < steps.size() && i < MAX_LINES; i++) {
            CraftingPlan.Step step = steps.get(i);
            boolean gather = step.kind() == CraftingPlan.Kind.GATHER;
            MessageManager.sendMessagePrefix((gather ? ChatFormatting.YELLOW : ChatFormatting.AQUA)
                    + "  " + (gather ? "gather " : "craft ") + step.count() + " " + step.item()
                    + (step.needsTable() ? ChatFormatting.GRAY + " (needs a crafting table)" : ""));
        }
        if (steps.size() > MAX_LINES) {
            MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "  ... and "
                    + (steps.size() - MAX_LINES) + " more");
        }
        MessageManager.sendMessagePrefix(ChatFormatting.GRAY
                + "Planning only - nothing is gathered or crafted yet.");
    }

    /** What the player is carrying, counted under the book's names. */
    private static Map<String, Integer> carried(Minecraft mc) {
        Map<String, Integer> have = new LinkedHashMap<>();
        if (mc == null || mc.player == null) return have;
        var inventory = mc.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            String name = GrindBook.generic(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            have.merge(name, stack.getCount(), Integer::sum);
        }
        return have;
    }

    private static void known() {
        MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "Known: " + GrindBook.LOG + ", "
                + GrindBook.PLANKS + ", " + GrindBook.STICK + ", " + GrindBook.CRAFTING_TABLE + ", "
                + GrindBook.COBBLESTONE + ", " + GrindBook.COAL + ", " + GrindBook.RAW_IRON + ", "
                + GrindBook.IRON_INGOT + ", " + GrindBook.FURNACE + ", " + GrindBook.WOODEN_PICKAXE
                + ", " + GrindBook.STONE_PICKAXE + ", " + GrindBook.IRON_PICKAXE);
    }
}
