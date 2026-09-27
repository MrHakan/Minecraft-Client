package me.mrhakan.agalarhack.commands.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.CraftingPlan;
import me.mrhakan.agalarhack.services.GrindBook;
import me.mrhakan.agalarhack.services.GrindExecutor;
import me.mrhakan.agalarhack.services.InventoryService;
import me.mrhakan.agalarhack.services.InventoryTransfers;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Plans a deterministic recipe chain, or starts its explicitly bounded raw-resource executor.
 *
 * <p>The short form remains plan-only for compatibility: {@code .grind stone_pickaxe} never starts
 * automation. Execution is opt-in through {@code .grind run}; it runs the supported recipe chain
 * through TaskRunner, vanilla world interactions, and the shared inventory transfer service.
 */
public class Grind extends Command {

    /** Kept small on purpose: a chat reply that scrolls the log away helps nobody. */
    private static final int MAX_LINES = 12;
    private static final EquipmentSlot[] NON_STORAGE_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.OFFHAND
    };

    public Grind() {
        super("grind", "Plans or executes a bounded resource grind",
                "grind <item> [count] | grind run <item> [count] | grind resume | grind stop | grind status", "plan");
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length < 2 || args[1].isBlank()) {
            sendUsage();
            known();
            return;
        }

        String selector = args[1].toLowerCase(Locale.ROOT);
        if ("stop".equals(selector)) {
            stop();
            return;
        }
        if ("status".equals(selector)) {
            status();
            return;
        }
        if ("resume".equals(selector)) {
            resume();
            return;
        }

        boolean explicitPlan = "plan".equals(selector);
        boolean execute = "run".equals(selector);
        int itemIndex = explicitPlan || execute ? 2 : 1;
        if (args.length <= itemIndex || args[itemIndex].isBlank()) {
            sendUsage();
            known();
            return;
        }
        if (args.length > itemIndex + 2) {
            sendUsage();
            return;
        }

        String target = GrindBook.generic(args[itemIndex]);
        if (!GrindBook.knows(target)) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "Nothing in the book for \""
                    + args[itemIndex] + "\".");
            known();
            return;
        }

        Integer wanted = count(args, itemIndex + 1);
        if (wanted == null) return;

        if (execute) {
            run(target, wanted);
        } else {
            plan(target, wanted);
        }
    }

    private static Integer count(String[] args, int index) {
        if (args.length <= index) return 1;
        try {
            int wanted = Integer.parseInt(args[index]);
            if (wanted < 1 || wanted > 512) {
                MessageManager.sendMessagePrefix(ChatFormatting.RED + "Ask for between 1 and 512.");
                return null;
            }
            return wanted;
        } catch (NumberFormatException notANumber) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "\"" + args[index]
                    + "\" is not a number.");
            return null;
        }
    }

    private static void run(String target, int wanted) {
        GrindExecutor executor = ClientServices.require(GrindExecutor.class);
        switch (executor.start(target, wanted)) {
            case STARTED -> MessageManager.sendMessagePrefix(ChatFormatting.GREEN
                    + "AutoGrind started for " + wanted + " " + target
                    + "; use .grind status to follow its steps.");
            case ALREADY_SATISFIED -> MessageManager.sendMessagePrefix(ChatFormatting.GREEN
                    + "You already have " + wanted + " " + target + ".");
            case BUSY -> MessageManager.sendMessagePrefix(ChatFormatting.RED
                    + "AutoGrind is already active; use .grind status to see its current step or pause reason.");
            case NO_WORLD -> MessageManager.sendMessagePrefix(ChatFormatting.RED
                    + "You need to be in a world to run AutoGrind.");
            case INVALID -> MessageManager.sendMessagePrefix(ChatFormatting.RED
                    + "That AutoGrind request is not valid.");
        }
    }

    private static void stop() {
        GrindExecutor executor = ClientServices.require(GrindExecutor.class);
        MessageManager.sendMessagePrefix(executor.stop() ? ChatFormatting.YELLOW
                + "AutoGrind stopped." : ChatFormatting.GRAY + "No AutoGrind run is active.");
    }

    private static void resume() {
        GrindExecutor executor = ClientServices.require(GrindExecutor.class);
        MessageManager.sendMessagePrefix(executor.resume() ? ChatFormatting.YELLOW
                + "AutoGrind resumed. Move closer to its target if it pauses again."
                : ChatFormatting.GRAY + "AutoGrind is not waiting for movement.");
    }

    private static void status() {
        GrindExecutor executor = ClientServices.require(GrindExecutor.class);
        switch (executor.state()) {
            case RUNNING -> MessageManager.sendMessagePrefix(ChatFormatting.GREEN + "AutoGrind running: "
                    + executor.currentTask() + " (" + executor.completed() + "/" + executor.total() + ").");
            case NEEDS_MOVEMENT -> MessageManager.sendMessagePrefix(ChatFormatting.YELLOW
                    + "AutoGrind needs movement: " + executor.blockedReason());
            case DONE -> MessageManager.sendMessagePrefix(ChatFormatting.GREEN + "AutoGrind complete.");
            case FAILED -> MessageManager.sendMessagePrefix(ChatFormatting.RED + "AutoGrind failed: "
                    + executor.failure());
            case IDLE -> MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "AutoGrind is idle.");
        }
    }

    /** What the player is carrying, counted under the book's names. */
    private static Map<String, Integer> carried() {
        Map<String, Integer> have = new LinkedHashMap<>();
        InventoryService inventory = ClientServices.require(InventoryService.class);
        for (int slot = 0; slot < InventoryTransfers.INVENTORY_SIZE; slot++) {
            addStack(have, inventory.stackAt(slot));
        }
        for (EquipmentSlot slot : NON_STORAGE_SLOTS) addStack(have, inventory.equipped(slot));
        return have;
    }

    private static void addStack(Map<String, Integer> have, ItemStack stack) {
        if (stack.isEmpty()) return;
        String name = GrindBook.generic(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        have.merge(name, stack.getCount(), Integer::sum);
    }

    private static void plan(String target, int wanted) {
        Map<String, Integer> have = carried();
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
            boolean smelt = GrindBook.IRON_INGOT.equals(step.item());
            String station = smelt ? " (needs a furnace)"
                    : step.needsTable() ? " (needs a crafting table)" : "";
            MessageManager.sendMessagePrefix((gather ? ChatFormatting.YELLOW : ChatFormatting.AQUA)
                    + "  " + (gather ? "gather " : smelt ? "smelt " : "craft ")
                    + step.count() + " " + step.item()
                    + (station.isEmpty() ? "" : ChatFormatting.GRAY + station));
        }
        if (steps.size() > MAX_LINES) {
            MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "  ... and "
                    + (steps.size() - MAX_LINES) + " more");
        }
        MessageManager.sendMessagePrefix(ChatFormatting.GRAY
                + "Planning only - nothing is gathered or crafted yet.");
    }

    private static void known() {
        MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "Known: " + GrindBook.LOG + ", "
                + GrindBook.PLANKS + ", " + GrindBook.STICK + ", " + GrindBook.CRAFTING_TABLE + ", "
                + GrindBook.COBBLESTONE + ", " + GrindBook.COAL + ", " + GrindBook.RAW_IRON + ", "
                + GrindBook.IRON_INGOT + ", " + GrindBook.FURNACE + ", " + GrindBook.WOODEN_PICKAXE
                + ", " + GrindBook.STONE_PICKAXE + ", " + GrindBook.IRON_PICKAXE);
    }
}
