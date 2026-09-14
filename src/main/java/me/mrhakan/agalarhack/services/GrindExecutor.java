package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * Runs the resource-gathering part of an AutoGrind plan through Baritone's Java API.
 *
 * <p>This is intentionally not a pathfinder and it never sends a chat command. It executes only
 * plans made entirely of raw gather steps, asks the optional Baritone bridge for the real quantity,
 * and stops when the inventory reaches that quantity. Crafted plans remain plan-only until a safe,
 * vanilla-compatible crafting executor has its own game-test evidence.
 *
 * <p>Ownership is explicit: a run is bound to the exact player and client level that started it.
 * A disconnect, world replacement or player replacement cancels the mining process and clears the
 * runner, so a goal from an old world cannot control the new one.
 */
public final class GrindExecutor {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("agalarhack-autogrind");
    private static final int MAX_GOAL = 512;
    private static final int IDLE_GRACE_TICKS = 80;

    public enum StartResult {
        STARTED,
        ALREADY_SATISFIED,
        BUSY,
        NO_WORLD,
        BARITONE_ABSENT,
        UNSUPPORTED,
        INVALID
    }

    private final Minecraft client;
    private final BaritoneBridge baritone;
    private final InventoryService inventory;
    private final TaskRunner runner = new TaskRunner();
    private LocalPlayer ownerPlayer;
    private ClientLevel ownerLevel;
    private boolean miningOwned;

    public GrindExecutor(Minecraft client, BaritoneBridge baritone, InventoryService inventory) {
        this.client = client;
        this.baritone = baritone;
        this.inventory = inventory;
    }

    /**
     * Starts a raw-resource run. Crafted steps are rejected before Baritone is touched, so a player
     * never sees a command claim it can craft when it cannot.
     */
    public StartResult start(String requestedTarget, int wanted) {
        String target = GrindBook.generic(requestedTarget);
        if (!GrindBook.knows(target) || wanted < 1 || wanted > MAX_GOAL) {
            return StartResult.INVALID;
        }
        if (runner.running()) return StartResult.BUSY;
        if (client == null || client.player == null || client.level == null) {
            return StartResult.NO_WORLD;
        }

        List<CraftingPlan.Step> steps;
        try {
            steps = CraftingPlan.plan(target, wanted, carried(), GrindBook.recipes());
        } catch (RuntimeException failure) {
            LOGGER.error("AutoGrind could not plan {} x{}", target, wanted, failure);
            return StartResult.INVALID;
        }
        if (steps.isEmpty()) return StartResult.ALREADY_SATISFIED;
        for (CraftingPlan.Step step : steps) {
            if (step.kind() != CraftingPlan.Kind.GATHER
                    || GrindBook.baritoneNames(step.item()).length == 0) {
                return StartResult.UNSUPPORTED;
            }
        }
        if (!baritone.available()) return StartResult.BARITONE_ABSENT;

        List<TaskRunner.Task> tasks = new ArrayList<>(steps.size());
        for (CraftingPlan.Step step : steps) {
            tasks.add(new MineTask(step.item(), step.count(), GrindBook.baritoneNames(step.item())));
        }
        ownerPlayer = client.player;
        ownerLevel = client.level;
        runner.start(tasks);
        return StartResult.STARTED;
    }

    /** Advances one bounded task tick on the client thread. */
    public void tick() {
        if (!runner.running()) return;
        if (client == null || client.player == null || client.level == null
                || client.player != ownerPlayer || client.level != ownerLevel
                || !client.player.isAlive()) {
            stop();
            return;
        }
        runner.tick();
        if (!runner.running()) {
            cancelOwnedMining();
            ownerPlayer = null;
            ownerLevel = null;
        }
    }

    /** Cancels a run and the mining process it owns. */
    public boolean stop() {
        boolean wasRunning = runner.running();
        runner.cancel();
        cancelOwnedMining();
        ownerPlayer = null;
        ownerLevel = null;
        return wasRunning;
    }

    /** Lifecycle alias used by world and connection cleanup. */
    public void reset() {
        stop();
    }

    public boolean running() { return runner.running(); }

    public TaskRunner.State state() { return runner.state(); }

    public String currentTask() { return runner.currentTask(); }

    public int completed() { return runner.completed(); }

    public int total() { return runner.total(); }

    public String failure() { return runner.failure(); }

    private void cancelOwnedMining() {
        if (!miningOwned) return;
        baritone.cancelMining();
        miningOwned = false;
    }

    private final class MineTask implements TaskRunner.Task {
        private final String item;
        private final int quantity;
        private final String[] blocks;
        private int goal = -1;
        private int idle;
        private boolean requested;

        private MineTask(String item, int quantity, String[] blocks) {
            this.item = item;
            this.quantity = quantity;
            this.blocks = blocks;
        }

        @Override
        public String name() {
            return "mine " + quantity + " " + item;
        }

        @Override
        public boolean satisfied() {
            if (goal < 0) return false;
            boolean done = count(item) >= goal;
            if (done && requested) {
                cancelOwnedMining();
                requested = false;
            }
            return done;
        }

        @Override
        public boolean tick() {
            int current = count(item);
            if (goal < 0) goal = current + quantity;
            if (current >= goal) return true;

            if (!requested) {
                BaritoneBridge.Result result = baritone.mineByName(quantity, blocks);
                if (result != BaritoneBridge.Result.STARTED) return false;
                requested = true;
                miningOwned = true;
                idle = 0;
                return true;
            }

            if (!baritone.mining() && !baritone.pathing()) {
                if (++idle >= IDLE_GRACE_TICKS) return false;
            } else {
                idle = 0;
            }
            return true;
        }

        @Override
        public void cancel() {
            if (requested) {
                cancelOwnedMining();
                requested = false;
            }
        }

        @Override
        public int budgetTicks() {
            return TaskRunner.DEFAULT_BUDGET_TICKS;
        }
    }

    /**
     * Counts through the shared inventory snapshot boundary instead of reading live stacks from a
     * second automation implementation. The client-thread tick makes each 36-slot snapshot coherent.
     */
    private Map<String, Integer> carried() {
        Map<String, Integer> have = new LinkedHashMap<>();
        if (client == null || client.player == null) return have;
        for (int slot = 0; slot < InventoryTransfers.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.stackAt(slot);
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            have.merge(GrindBook.generic(id), stack.getCount(), Integer::sum);
        }
        return have;
    }

    private int count(String generic) {
        if (client == null || client.player == null) return 0;
        int total = 0;
        for (int slot = 0; slot < InventoryTransfers.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.stackAt(slot);
            if (!stack.isEmpty()
                    && GrindBook.generic(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).equals(generic)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
