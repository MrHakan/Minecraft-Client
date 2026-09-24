package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import me.mrhakan.agalarhack.services.scanning.BlockScanCursor;
import me.mrhakan.agalarhack.services.scanning.ScanScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Executes every current GrindBook goal. CraftingPlan remains the inventory-only planner; this
 * executor adds the needed tools and stations, performs bounded resource scans, and uses vanilla
 * block and container interactions to realize each step.
 */
public final class GrindExecutor {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("agalarhack-autogrind");
    private static final String OWNER = "autogrind";
    private static final int PRIORITY = 50;
    private static final int MAX_GOAL = 512;
    private static final int SCAN_HORIZONTAL = 6;
    private static final int SCAN_VERTICAL = 4;
    private static final int SCAN_STEPS_PER_TICK = 512;
    private static final double MAX_REACH = 4.5;
    private static final int PICKUP_GRACE_TICKS = 60;
    private static final double PICKUP_HORIZONTAL_REACH = 1.75;
    private static final double PICKUP_VERTICAL_REACH = 2.5;
    private static final int SOURCE_OFFHAND = -2;
    private static final int HOTBAR_PENDING = -2;
    private static final EquipmentSlot[] NON_STORAGE_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.OFFHAND
    };

    public enum StartResult { STARTED, ALREADY_SATISFIED, BUSY, NO_WORLD, INVALID }

    private enum Station {
        TABLE(Blocks.CRAFTING_TABLE, GrindBook.CRAFTING_TABLE),
        FURNACE(Blocks.FURNACE, GrindBook.FURNACE);

        final Block block;
        final String item;
        Station(Block block, String item) { this.block = block; this.item = item; }
    }

    private final Minecraft client;
    private final InventoryService inventory;
    private final ScannerService scanner;
    private final RotationService rotations;
    private final TaskRunner runner = new TaskRunner();
    private LocalPlayer ownerPlayer;
    private ClientLevel ownerLevel;
    private BlockPos craftingTablePos;
    private BlockPos furnacePos;
    private int ownedStationMenuId = -1;

    public GrindExecutor(Minecraft client, InventoryService inventory, ScannerService scanner,
            RotationService rotations) {
        this.client = client;
        this.inventory = inventory;
        this.scanner = scanner;
        this.rotations = rotations;
    }

    /** Starts a complete execution plan derived from the same live inventory snapshot as the planner. */
    public StartResult start(String requestedTarget, int wanted) {
        String target = GrindBook.generic(requestedTarget);
        if (!GrindBook.knows(target) || wanted < 1 || wanted > MAX_GOAL) return StartResult.INVALID;
        if (runner.running() || runner.state() == TaskRunner.State.NEEDS_MOVEMENT) return StartResult.BUSY;
        runner.cancel();
        releaseControls();
        closeOwnedStationMenu();
        if (client == null || client.player == null || client.level == null || client.gameMode == null) {
            return StartResult.NO_WORLD;
        }

        Map<String, Integer> have = carried();
        try {
            if (CraftingPlan.plan(target, wanted, have, GrindBook.recipes()).isEmpty()) {
                runner.start(List.of());
                return StartResult.ALREADY_SATISFIED;
            }
            BlockPos[] stations = findNearbyStations();
            craftingTablePos = stations[0];
            furnacePos = stations[1];
            List<GrindExecutionPlan.Step> execution = GrindExecutionPlan.plan(target, wanted, have,
                    craftingTablePos != null, furnacePos != null);
            List<TaskRunner.Task> tasks = createTasks(execution, have);
            ownerPlayer = client.player;
            ownerLevel = client.level;
            ownedStationMenuId = -1;
            runner.start(tasks);
            return StartResult.STARTED;
        } catch (RuntimeException failure) {
            ownerPlayer = null;
            ownerLevel = null;
            LOGGER.error("AutoGrind could not build an execution plan for {} x{}", target, wanted, failure);
            return StartResult.INVALID;
        }
    }

    /** Resume a task paused for direct reach, an open screen, or a missing free inventory slot. */
    public boolean resume() { return runner.resume(); }

    /** Advances one task tick on the client thread. Container clicks remain owned by InventoryService. */
    public void tick() {
        if (!runner.running() && runner.state() != TaskRunner.State.NEEDS_MOVEMENT) return;
        if (client == null || client.player == null || client.level == null
                || client.player != ownerPlayer || client.level != ownerLevel || !client.player.isAlive()) {
            stop();
            return;
        }
        if (!runner.running()) return;
        String previousTask = runner.currentTask();
        runner.tick();
        String nextTask = runner.currentTask();
        TaskRunner.State state = runner.state();
        if (!java.util.Objects.equals(previousTask, nextTask) || state == TaskRunner.State.NEEDS_MOVEMENT
                || state == TaskRunner.State.DONE || state == TaskRunner.State.FAILED) releaseControls();
        if (state == TaskRunner.State.NEEDS_MOVEMENT) {
            // A paused task owns no player controls. The player needs to be free to move and look.
            rotations.release(OWNER);
        } else if (state == TaskRunner.State.DONE || state == TaskRunner.State.FAILED) {
            inventory.transfers().release(OWNER);
            closeOwnedStationMenu();
            ownerPlayer = null;
            ownerLevel = null;
        }
    }

    /** Cancels the current task and lets the shared transfer controller recover any carried stack. */
    public boolean stop() {
        boolean wasActive = runner.running() || runner.state() == TaskRunner.State.NEEDS_MOVEMENT;
        runner.cancel();
        releaseControls();
        inventory.transfers().release(OWNER);
        closeOwnedStationMenu();
        ownerPlayer = null;
        ownerLevel = null;
        return wasActive;
    }

    public void reset() { stop(); }
    public boolean running() { return runner.running(); }
    public TaskRunner.State state() { return runner.state(); }
    public String currentTask() { return runner.currentTask(); }
    public int completed() { return runner.completed(); }
    public int total() { return runner.total(); }
    public String failure() { return runner.failure(); }
    public String blockedReason() { return runner.blockedReason(); }

    private void releaseControls() {
        rotations.release(OWNER);
        inventory.release(OWNER);
    }

    private List<TaskRunner.Task> createTasks(List<GrindExecutionPlan.Step> plan,
            Map<String, Integer> startingInventory) {
        List<TaskRunner.Task> tasks = new ArrayList<>(plan.size());
        Map<String, Integer> simulated = new LinkedHashMap<>(startingInventory);
        for (int index = 0; index < plan.size(); index++) {
            GrindExecutionPlan.Step step = plan.get(index);
            GrindExecutionPlan.Step next = index + 1 < plan.size() ? plan.get(index + 1) : null;
            switch (step.action()) {
                case GATHER -> {
                    int goal = simulated.getOrDefault(step.item(), 0) + step.count();
                    tasks.add(new GatherResourceTask(step.item(), goal));
                    simulated.merge(step.item(), step.count(), Integer::sum);
                }
                case CRAFT -> {
                    tasks.add(new MoveOffhandTask(ingredients(step.item())));
                    int goal = simulated.getOrDefault(step.item(), 0) + step.count();
                    tasks.add(new CraftTask(step.item(), goal, step.needsTable()));
                    simulateRecipe(simulated, step.item(), step.count());
                }
                case PLACE_TABLE -> {
                    tasks.add(new PlaceStationTask(Station.TABLE));
                    simulated.merge(GrindBook.CRAFTING_TABLE, -1, Integer::sum);
                }
                case OPEN_TABLE -> {
                    if (next != null && next.action() == GrindExecutionPlan.Action.CRAFT) {
                        tasks.add(new MoveOffhandTask(ingredients(next.item())));
                    }
                    tasks.add(new OpenStationTask(Station.TABLE));
                }
                case PLACE_FURNACE -> {
                    tasks.add(new PlaceStationTask(Station.FURNACE));
                    simulated.merge(GrindBook.FURNACE, -1, Integer::sum);
                }
                case OPEN_FURNACE -> {
                    tasks.add(new MoveOffhandTask(Set.of(GrindBook.RAW_IRON, GrindBook.COAL)));
                    tasks.add(new OpenStationTask(Station.FURNACE));
                }
                case SMELT -> {
                    CraftingPlan.Recipe recipe = GrindBook.recipes().get(step.item());
                    int batches = step.count() / recipe.yield();
                    int rawIron = recipe.ingredients().getOrDefault(GrindBook.RAW_IRON, 0) * batches;
                    int fuel = recipe.ingredients().getOrDefault(GrindBook.COAL, 0) * batches;
                    int goal = simulated.getOrDefault(step.item(), 0) + step.count();
                    tasks.add(new SmeltTask(goal, rawIron, fuel));
                    simulateRecipe(simulated, step.item(), step.count());
                }
            }
        }
        return List.copyOf(tasks);
    }

    private static Set<String> ingredients(String item) {
        CraftingPlan.Recipe recipe = GrindBook.recipes().get(item);
        return recipe == null ? Set.of() : Set.copyOf(recipe.ingredients().keySet());
    }

    private static void simulateRecipe(Map<String, Integer> inventory, String item, int outputCount) {
        CraftingPlan.Recipe recipe = GrindBook.recipes().get(item);
        if (recipe == null) return;
        int batches = outputCount / recipe.yield();
        recipe.ingredients().forEach((ingredient, count) ->
                inventory.merge(ingredient, -count * batches, Integer::sum));
        inventory.merge(item, outputCount, Integer::sum);
    }

    /** A bounded, loaded-chunk-only lookup for existing stations close enough to reuse. */
    private BlockPos[] findNearbyStations() {
        BlockPos table = null, furnace = null;
        double tableDistance = Double.MAX_VALUE, furnaceDistance = Double.MAX_VALUE;
        BlockPos origin = client.player.blockPosition();
        Vec3 eye = client.player.getEyePosition();
        for (int dy = -SCAN_VERTICAL; dy <= SCAN_VERTICAL; dy++) {
            for (int dx = -SCAN_HORIZONTAL; dx <= SCAN_HORIZONTAL; dx++) {
                for (int dz = -SCAN_HORIZONTAL; dz <= SCAN_HORIZONTAL; dz++) {
                    int x = origin.getX() + dx, y = origin.getY() + dy, z = origin.getZ() + dz;
                    LevelChunk chunk = scanner.loadedChunk(x >> 4, z >> 4);
                    if (chunk == null || y < client.level.getMinY() || y >= client.level.getMaxY()) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = chunk.getBlockState(pos).getBlock();
                    double distance = eye.distanceToSqr(Vec3.atCenterOf(pos));
                    if (block == Station.TABLE.block && distance < tableDistance) {
                        tableDistance = distance;
                        table = pos.immutable();
                    } else if (block == Station.FURNACE.block && distance < furnaceDistance) {
                        furnaceDistance = distance;
                        furnace = pos.immutable();
                    }
                }
            }
        }
        return new BlockPos[]{table, furnace};
    }

    private BlockPos stationPosition(Station station) {
        return station == Station.TABLE ? craftingTablePos : furnacePos;
    }

    private void stationPosition(Station station, BlockPos pos) {
        if (station == Station.TABLE) craftingTablePos = pos == null ? null : pos.immutable();
        else furnacePos = pos == null ? null : pos.immutable();
    }

    private boolean stationMenuOpen(Station station) {
        if (client.player == null) return false;
        return station == Station.TABLE
                ? client.player.containerMenu instanceof CraftingMenu
                : client.player.containerMenu instanceof FurnaceMenu;
    }

    private boolean closeOwnedStationMenu() {
        if (ownedStationMenuId < 0 || client == null || client.player == null) return false;
        if (client.player.containerMenu != null && client.player.containerMenu.containerId == ownedStationMenuId
                && client.gui.screen() != null) {
            client.setScreen(null);
            ownedStationMenuId = -1;
            rotations.release(OWNER);
            inventory.release(OWNER);
            return true;
        }
        if (client.player.containerMenu == null || client.player.containerMenu.containerId != ownedStationMenuId) {
            ownedStationMenuId = -1;
        }
        return false;
    }

    private BlockPos findNearbyBlock(Block block) {
        if (client == null || client.player == null || client.level == null) return null;
        BlockPos origin = client.player.blockPosition();
        Vec3 eye = client.player.getEyePosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int dy = -SCAN_VERTICAL; dy <= SCAN_VERTICAL; dy++) {
            for (int dx = -SCAN_HORIZONTAL; dx <= SCAN_HORIZONTAL; dx++) {
                for (int dz = -SCAN_HORIZONTAL; dz <= SCAN_HORIZONTAL; dz++) {
                    int x = origin.getX() + dx, y = origin.getY() + dy, z = origin.getZ() + dz;
                    LevelChunk chunk = scanner.loadedChunk(x >> 4, z >> 4);
                    if (chunk == null || y < client.level.getMinY() || y >= client.level.getMaxY()) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!chunk.getBlockState(pos).is(block)) continue;
                    double distance = eye.distanceToSqr(Vec3.atCenterOf(pos));
                    if (distance < nearestDistance) { nearestDistance = distance; nearest = pos.immutable(); }
                }
            }
        }
        return nearest;
    }

    private boolean withinReach(BlockPos pos) {
        return client.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_REACH * MAX_REACH;
    }

    private BlockHitResult hitTarget(BlockPos pos) {
        Vec3 from = client.player.getEyePosition();
        Vec3 to = Vec3.atCenterOf(pos);
        var hit = client.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, client.player));
        return hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos) ? blockHit : null;
    }

    private void aimAt(BlockPos pos) {
        Vec3 delta = Vec3.atCenterOf(pos).subtract(client.player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        rotations.request(new RotationService.Request(OWNER, PRIORITY, RotationService.Mode.CLIENT,
                yaw, pitch, 360f, 180f, 180f, true));
    }

    private boolean aimedAt(BlockPos pos) {
        Vec3 delta = Vec3.atCenterOf(pos).subtract(client.player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        return Math.abs(Mth.wrapDegrees(yaw - client.player.getYRot())) <= 2.0f
                && Math.abs(pitch - client.player.getXRot()) <= 2.0f;
    }

    private int findHotbarItem(String generic) {
        for (int slot = 0; slot < InventoryTransfers.HOTBAR_SIZE; slot++) {
            if (countStack(inventory.stackAt(slot), generic) > 0) return slot;
        }
        return -1;
    }

    /** Returns a player inventory index, SOURCE_OFFHAND, or -1 when the item is not carried. */
    private int findCarriedSlot(String generic) {
        for (int slot = 0; slot < InventoryTransfers.INVENTORY_SIZE; slot++) {
            if (countStack(inventory.stackAt(slot), generic) > 0) return slot;
        }
        if (countStack(inventory.equipped(EquipmentSlot.OFFHAND), generic) > 0) return SOURCE_OFFHAND;
        return -1;
    }

    /** Moves a carried item into the hotbar through the shared owner/click channel when needed. */
    private int prepareHotbarItem(String generic) {
        int hotbar = findHotbarItem(generic);
        if (hotbar >= 0) return hotbar;
        int source = findCarriedSlot(generic);
        if (source < 0) return -1;
        if (inventory.transfers().busy()) return HOTBAR_PENDING;
        int destination = -1;
        for (int slot = 0; slot < InventoryTransfers.HOTBAR_SIZE; slot++) {
            if (inventory.stackAt(slot).isEmpty()) { destination = slot; break; }
        }
        if (destination < 0) destination = Math.max(0, inventory.selectedSlot());
        int sourceMenuSlot = source == SOURCE_OFFHAND
                ? InventoryTransfers.MENU_OFFHAND : InventoryTransfers.menuSlot(source);
        int[] clicks = {sourceMenuSlot, InventoryTransfers.menuSlot(destination), sourceMenuSlot};
        inventory.transfers().begin(OWNER, PRIORITY, clicks, 0);
        return HOTBAR_PENDING;
    }

    private int correctToolSlot(net.minecraft.world.level.block.state.BlockState state, String resource) {
        int bestSlot = -1;
        double bestSpeed = -1;
        for (int slot = 0; slot < InventoryTransfers.HOTBAR_SIZE; slot++) {
            ItemStack stack = inventory.stackAt(slot);
            if (!validTool(stack, state, resource)) continue;
            double speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) { bestSpeed = speed; bestSlot = slot; }
        }
        if (bestSlot >= 0) return bestSlot;

        int bestStorage = -1;
        bestSpeed = -1;
        for (int slot = InventoryTransfers.HOTBAR_SIZE; slot < InventoryTransfers.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.stackAt(slot);
            if (!validTool(stack, state, resource)) continue;
            double speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) { bestSpeed = speed; bestStorage = slot; }
        }
        if (bestStorage >= 0) {
            if (!inventory.transfers().busy()) {
                int destination = findEmptyHotbarSlot();
                if (destination < 0) destination = Math.max(0, inventory.selectedSlot());
                inventory.transfers().begin(OWNER, PRIORITY,
                        InventoryTransfers.equipPlan(bestStorage, InventoryTransfers.menuSlot(destination)), 0);
            }
            return HOTBAR_PENDING;
        }

        ItemStack offhand = inventory.equipped(EquipmentSlot.OFFHAND);
        if (validTool(offhand, state, resource)) {
            if (!inventory.transfers().busy()) {
                int destination = findEmptyHotbarSlot();
                if (destination < 0) destination = Math.max(0, inventory.selectedSlot());
                inventory.transfers().begin(OWNER, PRIORITY, new int[]{InventoryTransfers.MENU_OFFHAND,
                        InventoryTransfers.menuSlot(destination), InventoryTransfers.MENU_OFFHAND}, 0);
            }
            return HOTBAR_PENDING;
        }
        return -1;
    }

    private int findEmptyHotbarSlot() {
        for (int slot = 0; slot < InventoryTransfers.HOTBAR_SIZE; slot++) {
            if (inventory.stackAt(slot).isEmpty()) return slot;
        }
        return -1;
    }

    private static boolean validTool(ItemStack stack, net.minecraft.world.level.block.state.BlockState state,
            String resource) {
        if (stack.isEmpty() || (stack.isDamageableItem()
                && stack.getMaxDamage() - stack.getDamageValue() <= 1) || !stack.isCorrectToolForDrops(state)) {
            return false;
        }
        // Silk Touch changes the drop of stone and the two ores this executor understands. Do not
        // claim a resource task can finish with a tool that would leave its item count unchanged.
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        boolean silkChangesDrop = (GrindBook.COBBLESTONE.equals(resource)
                && (path.equals("stone") || path.equals("deepslate")))
                || GrindBook.COAL.equals(resource) && path.endsWith("_coal_ore")
                || GrindBook.RAW_IRON.equals(resource) && path.endsWith("_iron_ore");
        return !silkChangesDrop || !hasSilkTouch(stack);
    }

    private static boolean hasSilkTouch(ItemStack stack) {
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) return false;
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (holder.is(Enchantments.SILK_TOUCH) && enchantments.getLevel(holder) > 0) return true;
        }
        return false;
    }

    private String inventoryRecoveryReason() {
        return "Free an inventory slot so AutoGrind can return the carried stack, then run .grind resume.";
    }

    private static boolean isTarget(String generic, net.minecraft.world.level.block.state.BlockState state) {
        if (GrindBook.LOG.equals(generic)) return state.is(BlockTags.LOGS);
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return switch (generic) {
            case GrindBook.COBBLESTONE -> path.equals("stone") || path.equals("cobblestone")
                    || path.equals("deepslate") || path.equals("cobbled_deepslate");
            case GrindBook.COAL -> path.endsWith("_coal_ore");
            case GrindBook.RAW_IRON -> path.endsWith("_iron_ore");
            default -> false;
        };
    }

    private static String coordinates(BlockPos pos) {
        return pos == null ? "unknown" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /** Counts through the shared inventory snapshot boundary rather than a second inventory path. */
    private Map<String, Integer> carried() {
        Map<String, Integer> have = new LinkedHashMap<>();
        if (client == null || client.player == null) return have;
        for (int slot = 0; slot < InventoryTransfers.INVENTORY_SIZE; slot++) addStack(have, inventory.stackAt(slot));
        for (EquipmentSlot slot : NON_STORAGE_SLOTS) addStack(have, inventory.equipped(slot));
        return have;
    }

    private int count(String generic) {
        if (client == null || client.player == null) return 0;
        int total = 0;
        for (int slot = 0; slot < InventoryTransfers.INVENTORY_SIZE; slot++) {
            total += countStack(inventory.stackAt(slot), generic);
        }
        for (EquipmentSlot slot : NON_STORAGE_SLOTS) total += countStack(inventory.equipped(slot), generic);
        return total;
    }

    private static void addStack(Map<String, Integer> have, ItemStack stack) {
        if (stack.isEmpty()) return;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        have.merge(GrindBook.generic(id), stack.getCount(), Integer::sum);
    }

    private static int countStack(ItemStack stack, String generic) {
        return !stack.isEmpty()
                && GrindBook.generic(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).equals(generic)
                ? stack.getCount() : 0;
    }

    private final class GatherResourceTask implements TaskRunner.Task {
        private final String resource;
        private final int goal;
        private final BlockScanCursor cursor = new BlockScanCursor();
        private final ScanScheduler.Task scanTask = this::scanStep;
        private BlockPos target;
        private BlockPos nearestInReach;
        private BlockPos nearestOverall;
        private double nearestInReachDistance = Double.MAX_VALUE;
        private double nearestOverallDistance = Double.MAX_VALUE;
        private int scanCenterX, scanCenterY, scanCenterZ;
        private boolean scanStarted;
        private boolean scanReady;
        private boolean mining;
        private boolean waitingForPickup;
        private int pickupWait;
        private int inventoryBeforeMining;
        private BlockPos pickupPosition;
        private String movementReason;
        private String failureReason;

        GatherResourceTask(String resource, int goal) { this.resource = resource; this.goal = goal; }
        @Override public String name() { return "gather " + resource + " (" + count(resource) + "/" + goal + ")"; }
        @Override public boolean satisfied() { return count(resource) >= goal; }
        @Override public int budgetTicks() { return Math.max(TaskRunner.DEFAULT_BUDGET_TICKS, Math.min(70_000, (goal - count(resource)) * 100 + 4_000)); }

        @Override
        public boolean tick() {
            movementReason = null;
            if (failureReason != null) return false;
            if (satisfied()) return true;
            if (closeOwnedStationMenu()) return true;
            if (client.gui.screen() != null) {
                movementReason = "Close the open screen before AutoGrind can gather " + resource + ".";
                return true;
            }

            if (target != null) {
                if (!isTarget(resource, client.level.getBlockState(target))) {
                    stopBreaking();
                    pickupPosition = target.immutable();
                    target = null;
                    waitingForPickup = true;
                    pickupWait = 0;
                } else {
                    if (!withinReach(target)) {
                        movementReason = movementMessage(target, "outside direct interaction reach");
                        return true;
                    }
                    int toolSlot = -1;
                    if (!GrindBook.LOG.equals(resource)) {
                    toolSlot = correctToolSlot(client.level.getBlockState(target), resource);
                        if (toolSlot == HOTBAR_PENDING) {
                            if (inventory.transfers().recovering()) movementReason = inventoryRecoveryReason();
                            return true;
                        }
                        if (toolSlot < 0) {
                            failureReason = "no carried pickaxe can harvest the " + resource + " at " + coordinates(target);
                            return false;
                        }
                        if (!inventory.select(OWNER, PRIORITY, toolSlot, false, true)) return true;
                    }
                    aimAt(target);
                    if (!aimedAt(target)) return true;
                    BlockHitResult hit = hitTarget(target);
                    if (hit == null) {
                        movementReason = movementMessage(target, "blocked from direct interaction");
                        return true;
                    }
                    if (client.gameMode == null) {
                        failureReason = "lost the active game mode while breaking " + coordinates(target);
                        return false;
                    }
                    if (!mining) {
                        inventoryBeforeMining = count(resource);
                        client.gameMode.startDestroyBlock(target, hit.getDirection());
                        mining = true;
                    } else {
                        client.gameMode.continueDestroyBlock(target, hit.getDirection());
                    }
                    return true;
                }
            }

            if (waitingForPickup) {
                if (count(resource) > inventoryBeforeMining) {
                    waitingForPickup = false;
                    pickupPosition = null;
                    pickupWait = 0;
                } else {
                    if (pickupWait >= PICKUP_GRACE_TICKS) {
                        if (nearPickupPosition()) pickupWait = 0;
                        else {
                            movementReason = pickupMovementReason();
                            return true;
                        }
                    }
                    if (++pickupWait < PICKUP_GRACE_TICKS) return true;
                    movementReason = pickupMovementReason();
                    return true;
                }
            }

            if (scanReady) {
                scanReady = false;
                target = nearestInReach != null ? nearestInReach : nearestOverall;
                if (target == null) {
                    failureReason = "found no " + resource + " block in loaded chunks within "
                            + SCAN_HORIZONTAL + " blocks; no block was broken";
                    return false;
                }
                if (!withinReach(target)) {
                    movementReason = movementMessage(target,
                            "movement automation unavailable; move closer and run .grind resume");
                    return true;
                }
            } else {
                prepareScan();
                scanner.offerClientTask(this, ScanScheduler.Priority.NEAR, SCAN_STEPS_PER_TICK, scanTask);
            }
            return true;
        }

        private ScanScheduler.Result scanStep(ScanScheduler.Budget budget) {
            try {
                return scanStepBounded(budget);
            } catch (RuntimeException failure) {
                failureReason = resource + " scan failed: " + failure.getMessage();
                LOGGER.error("AutoGrind {} scan failed", resource, failure);
                return ScanScheduler.Result.DONE;
            }
        }

        private ScanScheduler.Result scanStepBounded(ScanScheduler.Budget budget) {
            if (cursor.cycle() > 0) { scanReady = true; return ScanScheduler.Result.DONE; }
            int x = cursor.x(), y = cursor.y(), z = cursor.z();
            if (!scanner.reserveBlock(budget, x, z)) return ScanScheduler.Result.BLOCKED;
            LevelChunk chunk = scanner.loadedChunk(x >> 4, z >> 4);
            if (chunk == null) {
                cursor.skipChunk();
                if (cursor.cycle() > 0) { scanReady = true; return ScanScheduler.Result.DONE; }
                return ScanScheduler.Result.MORE;
            }
            if (y >= client.level.getMinY() && y < client.level.getMaxY()) {
                BlockPos pos = new BlockPos(x, y, z);
                if (isTarget(resource, chunk.getBlockState(pos))) consider(pos);
            }
            cursor.advance();
            if (cursor.cycle() > 0) { scanReady = true; return ScanScheduler.Result.DONE; }
            return ScanScheduler.Result.MORE;
        }

        private void consider(BlockPos pos) {
            double distance = client.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < nearestOverallDistance) {
                nearestOverallDistance = distance;
                nearestOverall = pos.immutable();
            }
            // RotationService turns before the executor verifies the actual ray hit.
            if (distance <= MAX_REACH * MAX_REACH && distance < nearestInReachDistance) {
                nearestInReachDistance = distance;
                nearestInReach = pos.immutable();
            }
        }

        private void prepareScan() {
            BlockPos center = client.player.blockPosition();
            if (scanStarted && Math.abs(center.getX() - scanCenterX) <= 1
                    && Math.abs(center.getY() - scanCenterY) <= 1
                    && Math.abs(center.getZ() - scanCenterZ) <= 1) return;
            scanCenterX = center.getX(); scanCenterY = center.getY(); scanCenterZ = center.getZ();
            cursor.reset(scanCenterX, scanCenterY, scanCenterZ, SCAN_HORIZONTAL, SCAN_VERTICAL);
            nearestInReach = null;
            nearestOverall = null;
            nearestInReachDistance = Double.MAX_VALUE;
            nearestOverallDistance = Double.MAX_VALUE;
            scanStarted = true;
        }

        private boolean nearPickupPosition() {
            if (pickupPosition == null || client.player == null) return false;
            Vec3 player = client.player.position(), drop = Vec3.atCenterOf(pickupPosition);
            double x = player.x - drop.x, z = player.z - drop.z;
            return x * x + z * z <= PICKUP_HORIZONTAL_REACH * PICKUP_HORIZONTAL_REACH
                    && Math.abs(player.y - drop.y) <= PICKUP_VERTICAL_REACH;
        }

        private String pickupMovementReason() {
            return "The " + resource + " drop at " + coordinates(pickupPosition)
                    + " is not in your inventory; move over it and run .grind resume.";
        }

        private String movementMessage(BlockPos pos, String reason) {
            return "Target " + resource + " block at " + coordinates(pos) + " — " + reason
                    + "; movement automation unavailable without a verified 26.2 pathing backend.";
        }

        @Override public String blockedReason() { return movementReason; }
        @Override public String failureReason() { return failureReason; }

        @Override
        public void cancel() {
            scanner.cancel(this);
            stopBreaking();
            rotations.release(OWNER);
            inventory.release(OWNER);
        }

        private void stopBreaking() {
            if (mining && client.gameMode != null) client.gameMode.stopDestroyBlock();
            mining = false;
            inventory.release(OWNER);
        }
    }

    /** Moves relevant offhand ingredients into the normal inventory before opening a station menu. */
    private final class MoveOffhandTask implements TaskRunner.Task {
        private final Set<String> relevant;
        private String movementReason;
        private String failureReason;

        MoveOffhandTask(Set<String> relevant) { this.relevant = relevant; }
        @Override public String name() { return "prepare crafting inputs"; }
        @Override public boolean satisfied() {
            ItemStack stack = inventory.equipped(EquipmentSlot.OFFHAND);
            return stack.isEmpty() || !relevant.contains(GrindBook.generic(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        }

        @Override public boolean tick() {
            movementReason = null;
            if (satisfied()) return true;
            if (closeOwnedStationMenu()) return true;
            if (client.gui.screen() != null || client.player.containerMenu != client.player.inventoryMenu) {
                movementReason = "Close the open screen before AutoGrind can move an offhand ingredient.";
                return true;
            }
            if (inventory.transfers().busy()) return true;
            int destination = findEmptyHotbarSlot();
            if (destination < 0) destination = Math.max(0, inventory.selectedSlot());
            if (!inventory.transfers().begin(OWNER, PRIORITY, new int[]{InventoryTransfers.MENU_OFFHAND,
                    InventoryTransfers.menuSlot(destination), InventoryTransfers.MENU_OFFHAND}, 0)) return true;
            return true;
        }
        @Override public String blockedReason() { return movementReason; }
        @Override public String failureReason() { return failureReason; }
        @Override public void cancel() { inventory.transfers().release(OWNER); }
    }

    private final class PlaceStationTask implements TaskRunner.Task {
        private final Station station;
        private int attempt;
        private int retryWait;
        private BlockPos pendingPlace;
        private boolean awaitingPlacement;
        private int placementWaitTicks;
        private String movementReason;
        private String failureReason;

        PlaceStationTask(Station station) { this.station = station; }
        @Override public String name() { return "place " + station.item; }
        @Override public boolean satisfied() {
            BlockPos pos = stationPosition(station);
            return pos != null && client.level.getBlockState(pos).is(station.block);
        }

        @Override public boolean tick() {
            movementReason = null;
            if (failureReason != null) return false;
            if (satisfied()) return true;
            if (closeOwnedStationMenu()) return true;
            if (client.gui.screen() != null || client.player.containerMenu != client.player.inventoryMenu) {
                movementReason = "Close the open screen before AutoGrind can place " + station.item + ".";
                return true;
            }
            if (awaitingPlacement) {
                if (++placementWaitTicks <= 40) return true;
                awaitingPlacement = false;
                placementWaitTicks = 0;
                attempt++;
                pendingPlace = null;
                retryWait = 2;
                return true;
            }
            if (retryWait > 0) { retryWait--; return true; }
            if (attempt >= 4) {
                failureReason = "could not place " + station.item + " on a nearby clear solid surface";
                return false;
            }
            int hotbar = prepareHotbarItem(station.item);
            if (hotbar == HOTBAR_PENDING) {
                if (inventory.transfers().recovering()) movementReason = inventoryRecoveryReason();
                return true;
            }
            if (hotbar < 0) {
                failureReason = "the " + station.item + " is not in the inventory";
                return false;
            }
            if (pendingPlace == null) pendingPlace = placementCandidate(attempt);
            if (pendingPlace == null) {
                movementReason = "Stand near a clear solid surface to place " + station.item + ".";
                return true;
            }
            BlockPos place = pendingPlace;
            BlockPos support = place.below();
            if (!withinReach(support)) {
                movementReason = "Move within reach of the surface at " + coordinates(support)
                        + " to place " + station.item + ".";
                return true;
            }
            if (!inventory.select(OWNER, PRIORITY, hotbar, false, true)) return true;
            aimAt(support);
            if (!aimedAt(support)) return true;
            BlockHitResult hit = hitTarget(support);
            if (hit == null || client.gameMode == null) {
                attempt++;
                pendingPlace = null;
                retryWait = 4;
                inventory.release(OWNER);
                rotations.release(OWNER);
                return true;
            }
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
            stationPosition(station, place);
            awaitingPlacement = true;
            placementWaitTicks = 0;
            inventory.release(OWNER);
            rotations.release(OWNER);
            return true;
        }

        private BlockPos placementCandidate(int index) {
            Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
            BlockPos feet = client.player.blockPosition();
            for (int offset = 0; offset < sides.length; offset++) {
                BlockPos pos = feet.relative(sides[(index + offset) % sides.length]);
                BlockPos support = pos.below();
                LevelChunk chunk = scanner.loadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk == null || !chunk.getBlockState(pos).isAir()) continue;
                if (client.level.getBlockState(support).getCollisionShape(client.level, support).isEmpty()) continue;
                if (withinReach(support)) return pos.immutable();
            }
            return null;
        }

        @Override public String blockedReason() { return movementReason; }
        @Override public String failureReason() { return failureReason; }
        @Override public void cancel() { inventory.release(OWNER); rotations.release(OWNER); inventory.transfers().release(OWNER); }
    }

    private final class OpenStationTask implements TaskRunner.Task {
        private final Station station;
        private boolean interactionIssued;
        private int waitTicks;
        private String movementReason;
        private String failureReason;

        OpenStationTask(Station station) { this.station = station; }
        @Override public String name() { return "open " + station.item; }

        @Override public boolean satisfied() {
            if (!stationMenuOpen(station)) return false;
            if (interactionIssued) ownedStationMenuId = client.player.containerMenu.containerId;
            return true;
        }

        @Override public boolean tick() {
            movementReason = null;
            if (failureReason != null) return false;
            if (satisfied()) return true;
            if (client.gui.screen() != null) {
                if (closeOwnedStationMenu()) return true;
                movementReason = "Close the open screen before AutoGrind can open " + station.item + ".";
                return true;
            }
            if (client.player.containerMenu != client.player.inventoryMenu) return true;
            if (interactionIssued) {
                if (++waitTicks > 40) {
                    failureReason = "vanilla did not open the " + station.item + " menu";
                    return false;
                }
                return true;
            }
            BlockPos pos = stationPosition(station);
            if (pos == null || !client.level.getBlockState(pos).is(station.block)) {
                pos = findNearbyBlock(station.block);
                stationPosition(station, pos);
            }
            if (pos == null) {
                failureReason = "no placed " + station.item + " was found in loaded chunks nearby";
                return false;
            }
            if (!withinReach(pos)) {
                movementReason = "Target " + station.item + " at " + coordinates(pos)
                        + " is outside reach; movement automation is unavailable.";
                return true;
            }
            aimAt(pos);
            if (!aimedAt(pos)) return true;
            BlockHitResult hit = hitTarget(pos);
            if (hit == null) {
                movementReason = "Target " + station.item + " at " + coordinates(pos)
                        + " is blocked from direct interaction.";
                return true;
            }
            int handSlot = safeInteractionSlot();
            if (handSlot < 0) {
                movementReason = "Free a hotbar slot or keep a non-block item there before opening " + station.item + ".";
                return true;
            }
            if (!inventory.select(OWNER, PRIORITY, handSlot, false, true)) return true;
            if (client.gameMode == null) return true;
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
            inventory.release(OWNER);
            rotations.release(OWNER);
            interactionIssued = true;
            return true;
        }

        private int safeInteractionSlot() {
            for (int slot = 0; slot < InventoryTransfers.HOTBAR_SIZE; slot++) {
                if (inventory.stackAt(slot).isEmpty()) return slot;
            }
            for (int slot = 0; slot < InventoryTransfers.HOTBAR_SIZE; slot++) {
                if (!(inventory.stackAt(slot).getItem() instanceof BlockItem)) return slot;
            }
            return -1;
        }

        @Override public String blockedReason() { return movementReason; }
        @Override public String failureReason() { return failureReason; }
        @Override public void cancel() { rotations.release(OWNER); inventory.release(OWNER); closeOwnedStationMenu(); }
    }

    private final class CraftTask implements TaskRunner.Task {
        private final String item;
        private final int goal;
        private final boolean requiresTable;
        private boolean ingredientClicksQueued;
        private boolean resultClickQueued;
        private int resultWaitTicks;
        private int outputCountBeforeClick;
        private String movementReason;
        private String failureReason;

        CraftTask(String item, int goal, boolean requiresTable) {
            this.item = item; this.goal = goal; this.requiresTable = requiresTable;
        }
        @Override public String name() { return "craft " + item + " (" + count(item) + "/" + goal + ")"; }
        @Override public boolean satisfied() { return count(item) >= goal; }
        @Override public int budgetTicks() {
            CraftingPlan.Recipe recipe = GrindBook.recipes().get(item);
            int recipes = recipe == null ? 1 : Math.max(1, Math.ceilDiv(goal - count(item), recipe.yield()));
            return Math.min(70_000, Math.max(TaskRunner.DEFAULT_BUDGET_TICKS, recipes * 40 + 2_000));
        }

        @Override public boolean tick() {
            movementReason = null;
            if (failureReason != null) return false;
            if (satisfied()) return true;
            CraftingPlan.Recipe recipe = GrindBook.recipes().get(item);
            if (recipe == null || GrindRecipeLayouts.inputs(item, requiresTable).isEmpty()) {
                failureReason = "no vanilla crafting layout is registered for " + item;
                return false;
            }
            boolean tableMenu = client.player.containerMenu instanceof CraftingMenu;
            if (requiresTable && !tableMenu) {
                if (closeOwnedStationMenu()) return true;
                movementReason = "Open the nearby crafting table and run .grind resume.";
                return true;
            }
            if (!tableMenu && (client.gui.screen() != null || client.player.containerMenu != client.player.inventoryMenu)) {
                if (closeOwnedStationMenu()) return true;
                movementReason = "Close the open screen before AutoGrind can craft " + item + ".";
                return true;
            }
            if (inventory.transfers().busy()) {
                if (inventory.transfers().recovering()) movementReason = inventoryRecoveryReason();
                return true;
            }
            int menuId = tableMenu ? client.player.containerMenu.containerId : -1;
            boolean table = tableMenu;
            if (ingredientClicksQueued) {
                if (++resultWaitTicks < 3) return true;
                // A station screen can close after only part of its click plan has run. The recipe
                // layout check below sees the cells already placed and queues only the missing ones.
                ingredientClicksQueued = false;
            }
            if (resultClickQueued) {
                if (satisfied()) return true;
                if (count(item) <= outputCountBeforeClick) {
                    if (++resultWaitTicks > 10 && resultItem(menuId).equals(item)) {
                        resultClickQueued = false;
                        resultWaitTicks = 0;
                        return true;
                    }
                    if (resultWaitTicks > 40) {
                        failureReason = "the crafted " + item + " output did not reach the inventory";
                        return false;
                    }
                    return true;
                }
                resultClickQueued = false;
            }
            if (resultItem(menuId).equals(item)) {
                if (!inventory.transfers().beginClicks(OWNER, PRIORITY, menuId,
                        new ContainerTransferController.Click[]{new ContainerTransferController.Click(0, 0)}, 0)) {
                    return true;
                }
                resultClickQueued = true;
                outputCountBeforeClick = count(item);
                resultWaitTicks = 0;
                return true;
            }
            List<ContainerTransferController.Click> clicks = craftingInputClicks(item, table, menuId);
            if (clicks == null) {
                failureReason = "missing ingredients or a clear crafting grid for " + item;
                return false;
            }
            if (clicks.isEmpty()) {
                if (++resultWaitTicks > 30) {
                    failureReason = "the placed ingredients did not produce the vanilla " + item + " result";
                    return false;
                }
                return true;
            }
            if (!inventory.transfers().beginClicks(OWNER, PRIORITY, menuId,
                    clicks.toArray(ContainerTransferController.Click[]::new), 0)) return true;
            ingredientClicksQueued = true;
            resultWaitTicks = 0;
            return true;
        }

        @Override public String blockedReason() { return movementReason; }
        @Override public String failureReason() { return failureReason; }
        @Override public void cancel() { inventory.transfers().release(OWNER); }
    }

    private List<ContainerTransferController.Click> craftingInputClicks(String output, boolean table, int menuId) {
        CraftingPlan.Recipe recipe = GrindBook.recipes().get(output);
        Map<String, List<Integer>> layout = GrindRecipeLayouts.inputs(output, table);
        int gridSize = table ? 9 : 4;
        Map<Integer, String> expected = new LinkedHashMap<>();
        for (var ingredient : layout.entrySet()) {
            for (int cell : ingredient.getValue()) expected.put(cell + 1, ingredient.getKey());
        }
        Map<String, List<Integer>> emptyTargets = new LinkedHashMap<>();
        for (int slot = 1; slot <= gridSize; slot++) {
            ItemStack placed = client.player.containerMenu.getSlot(slot).getItem();
            String wanted = expected.get(slot);
            if (placed.isEmpty()) {
                if (wanted != null) emptyTargets.computeIfAbsent(wanted, ignored -> new ArrayList<>()).add(slot);
            } else if (wanted == null || !wanted.equals(
                    GrindBook.generic(BuiltInRegistries.ITEM.getKey(placed.getItem()).toString()))) {
                return null;
            }
        }
        InventoryTransfers.PlayerMenuLayout inventoryLayout = table
                ? InventoryTransfers.PlayerMenuLayout.CRAFTING_TABLE : InventoryTransfers.PlayerMenuLayout.INVENTORY;
        List<ContainerTransferController.Click> clicks = new ArrayList<>();
        for (String ingredient : new TreeSet<>(recipe.ingredients().keySet())) {
            List<Integer> targetSlots = emptyTargets.getOrDefault(ingredient, List.of());
            if (!appendIngredientClicks(clicks, ingredient, targetSlots, inventoryLayout, table)) return null;
        }
        return clicks;
    }

    private boolean appendIngredientClicks(List<ContainerTransferController.Click> clicks, String ingredient,
            List<Integer> targetSlots, InventoryTransfers.PlayerMenuLayout layout, boolean tableMenu) {
        int remaining = targetSlots.size();
        for (int index = 0; index < InventoryTransfers.INVENTORY_SIZE && remaining > 0; index++) {
            ItemStack source = inventory.stackAt(index);
            int available = countStack(source, ingredient);
            if (available <= 0) continue;
            int amount = Math.min(available, remaining);
            int menuSlot = InventoryTransfers.menuSlot(index, layout);
            clicks.add(new ContainerTransferController.Click(menuSlot, 0));
            for (int i = 0; i < amount; i++) {
                int gridSlot = targetSlots.get(targetSlots.size() - remaining + i);
                clicks.add(new ContainerTransferController.Click(gridSlot, 1));
            }
            if (amount < available) clicks.add(new ContainerTransferController.Click(menuSlot, 0));
            remaining -= amount;
        }
        if (!tableMenu && remaining > 0) {
            ItemStack offhand = inventory.equipped(EquipmentSlot.OFFHAND);
            int available = countStack(offhand, ingredient);
            int amount = Math.min(available, remaining);
            if (amount > 0) {
                clicks.add(new ContainerTransferController.Click(InventoryTransfers.MENU_OFFHAND, 0));
                for (int i = 0; i < amount; i++) {
                    int gridSlot = targetSlots.get(targetSlots.size() - remaining + i);
                    clicks.add(new ContainerTransferController.Click(gridSlot, 1));
                }
                if (amount < available) clicks.add(new ContainerTransferController.Click(InventoryTransfers.MENU_OFFHAND, 0));
                remaining -= amount;
            }
        }
        return remaining == 0;
    }

    private String resultItem(int menuId) {
        if (client.player == null || client.player.containerMenu == null
                || (menuId >= 0 && client.player.containerMenu.containerId != menuId)) return "";
        ItemStack output = client.player.containerMenu.getSlot(0).getItem();
        return output.isEmpty() ? "" : GrindBook.generic(BuiltInRegistries.ITEM.getKey(output.getItem()).toString());
    }

    private final class SmeltTask implements TaskRunner.Task {
        private final int goal;
        private final int rawIron;
        private final int coal;
        private boolean initialized;
        private boolean outputClickQueued;
        private int initialIngotCount;
        private int initialCoalCount;
        private int outputCountBeforeClick;
        private int resultWaitTicks;
        private String movementReason;
        private String failureReason;

        SmeltTask(int goal, int rawIron, int coal) { this.goal = goal; this.rawIron = rawIron; this.coal = coal; }
        @Override public String name() { return "smelt iron_ingot (" + count(GrindBook.IRON_INGOT) + "/" + goal + ")"; }
        @Override public boolean satisfied() { return count(GrindBook.IRON_INGOT) >= goal; }
        @Override public int budgetTicks() { return Math.min(140_000, Math.max(TaskRunner.DEFAULT_BUDGET_TICKS, rawIron * 220 + 4_000)); }

        @Override public boolean tick() {
            movementReason = null;
            if (failureReason != null) return false;
            if (satisfied()) return true;
            if (!(client.player.containerMenu instanceof FurnaceMenu menu)) {
                if (closeOwnedStationMenu()) return true;
                movementReason = "Open the nearby furnace and run .grind resume.";
                return true;
            }
            if (inventory.transfers().busy()) {
                if (inventory.transfers().recovering()) movementReason = inventoryRecoveryReason();
                return true;
            }
            int menuId = menu.containerId;
            if (!initialized) {
                if (!menu.getSlot(0).getItem().isEmpty() || !menu.getSlot(1).getItem().isEmpty()) {
                    failureReason = "the nearby furnace already contains items; AutoGrind left them untouched";
                    return false;
                }
                initialIngotCount = count(GrindBook.IRON_INGOT);
                initialCoalCount = count(GrindBook.COAL);
                initialized = true;
            }
            if (outputClickQueued) {
                if (satisfied()) return true;
                if (count(GrindBook.IRON_INGOT) <= outputCountBeforeClick) {
                    resultWaitTicks++;
                    if (resultWaitTicks > 10 && !menu.getSlot(2).getItem().isEmpty()) {
                        outputClickQueued = false;
                        resultWaitTicks = 0;
                        return true;
                    }
                    if (resultWaitTicks > 40) {
                        failureReason = "the furnace output did not reach the inventory";
                        return false;
                    }
                    return true;
                }
                outputClickQueued = false;
            }
            ItemStack result = menu.getSlot(2).getItem();
            if (!result.isEmpty()) {
                if (!GrindBook.IRON_INGOT.equals(
                        GrindBook.generic(BuiltInRegistries.ITEM.getKey(result.getItem()).toString()))) {
                    failureReason = "the furnace contains an unrelated output; AutoGrind left it untouched";
                    return false;
                }
                outputCountBeforeClick = count(GrindBook.IRON_INGOT);
                if (!inventory.transfers().beginClicks(OWNER, PRIORITY, menuId,
                        new ContainerTransferController.Click[]{new ContainerTransferController.Click(2, 0)}, 0)) return true;
                outputClickQueued = true;
                resultWaitTicks = 0;
                return true;
            }

            ItemStack input = menu.getSlot(0).getItem();
            if (!input.isEmpty() && !GrindBook.RAW_IRON.equals(
                    GrindBook.generic(BuiltInRegistries.ITEM.getKey(input.getItem()).toString()))) {
                failureReason = "the furnace input is not raw iron; AutoGrind left it untouched";
                return false;
            }
            ItemStack fuel = menu.getSlot(1).getItem();
            if (!fuel.isEmpty() && !GrindBook.COAL.equals(
                    GrindBook.generic(BuiltInRegistries.ITEM.getKey(fuel.getItem()).toString()))) {
                failureReason = "the furnace fuel slot contains an unrelated item; AutoGrind left it untouched";
                return false;
            }

            // Raw iron already cooking, output waiting in the result slot, and ingots already
            // collected all count toward the same task. This lets a resumed task continue after
            // the click queue was safely cancelled when a station screen closed.
            int produced = Math.max(0, count(GrindBook.IRON_INGOT) - initialIngotCount);
            int availableOutput = 0;
            ItemStack pendingOutput = menu.getSlot(2).getItem();
            if (!pendingOutput.isEmpty()) availableOutput = pendingOutput.getCount();
            int inputInFurnace = input.isEmpty() ? 0 : input.getCount();
            int remainingRaw = Math.max(0, rawIron - produced - availableOutput - inputInFurnace);
            int fuelMoved = Math.max(0, initialCoalCount - count(GrindBook.COAL));
            int remainingFuel = Math.max(0, coal - fuelMoved);
            int rawSlotRoom = Math.max(0, 64 - inputInFurnace);
            int loadRaw = Math.min(remainingRaw, rawSlotRoom);
            if (loadRaw > 0 || remainingFuel > 0) {
                if (count(GrindBook.RAW_IRON) < loadRaw || count(GrindBook.COAL) < remainingFuel) {
                    failureReason = "missing raw iron or coal in the inventory for the remaining smelting batch";
                    return false;
                }
                List<ContainerTransferController.Click> clicks = new ArrayList<>();
                if (loadRaw > 0 && !appendFurnaceInput(clicks, GrindBook.RAW_IRON, loadRaw, 0)
                        || remainingFuel > 0 && !appendFurnaceInput(clicks, GrindBook.COAL, remainingFuel, 1)) {
                    failureReason = "could not prepare the remaining raw iron or coal for smelting";
                    return false;
                }
                if (!inventory.transfers().beginClicks(OWNER, PRIORITY, menuId,
                        clicks.toArray(ContainerTransferController.Click[]::new), 0)) return true;
                return true;
            }
            return true;
        }

        private boolean appendFurnaceInput(List<ContainerTransferController.Click> clicks,
                String ingredient, int amount, int furnaceSlot) {
            return appendIngredientClicks(clicks, ingredient, java.util.Collections.nCopies(amount, furnaceSlot),
                    InventoryTransfers.PlayerMenuLayout.FURNACE, true);
        }

        @Override public String blockedReason() { return movementReason; }
        @Override public String failureReason() { return failureReason; }
        @Override public void cancel() { inventory.transfers().release(OWNER); }
    }

}
