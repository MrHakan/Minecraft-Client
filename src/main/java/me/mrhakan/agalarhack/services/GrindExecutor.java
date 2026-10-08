package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Executes every current GrindBook goal. CraftingPlan remains the inventory-only planner; this
 * executor adds the needed tools and stations, performs bounded resource scans, and uses vanilla
 * block and container interactions to realize each step.
 */
public final class GrindExecutor {
    static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("agalarhack-autogrind");
    static final String OWNER = "autogrind";
    static final int PRIORITY = 50;
    private static final int MAX_GOAL = 512;
    static final int SCAN_HORIZONTAL = 6;
    static final int SCAN_VERTICAL = 4;
    static final int SCAN_STEPS_PER_TICK = 512;
    static final double MAX_REACH = 4.5;
    static final int PICKUP_GRACE_TICKS = 60;
    static final double PICKUP_HORIZONTAL_REACH = 1.75;
    static final double PICKUP_VERTICAL_REACH = 2.5;
    private static final int SOURCE_OFFHAND = -2;
    static final int HOTBAR_PENDING = -2;
    private static final EquipmentSlot[] NON_STORAGE_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.OFFHAND
    };

    public enum StartResult { STARTED, ALREADY_SATISFIED, BUSY, NO_WORLD, INVALID }

    enum Station {
        TABLE(Blocks.CRAFTING_TABLE, GrindBook.CRAFTING_TABLE),
        FURNACE(Blocks.FURNACE, GrindBook.FURNACE);

        final Block block;
        final String item;
        Station(Block block, String item) { this.block = block; this.item = item; }
    }

    final Minecraft client;
    final InventoryService inventory;
    final ScannerService scanner;
    final RotationService rotations;
    final BaritoneBridge baritone;
    boolean useBaritone;
    Map<String, Integer> campaignReserves = Map.of();
    boolean survival;
    private boolean changingDimension;
    private int transitionWait;
    private SurvivalTasks survivalTasks;
    private BlockPos survivalBase;
    private List<BlockPos> survivalStorage = List.of();
    private ClientLevel homeWorld;
    private final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Set<BlockPos>> protectedBlocks = new LinkedHashMap<>();
    private final TaskRunner runner = new TaskRunner();
    private LocalPlayer ownerPlayer;
    private ClientLevel ownerLevel;
    final GrindTravel travel = new GrindTravel(this);
    final GrindStations stations = new GrindStations(this);
    final GrindReach reach = new GrindReach(this);

    public GrindExecutor(Minecraft client, InventoryService inventory, ScannerService scanner,
            RotationService rotations) {
        this(client, inventory, scanner, rotations, new BaritoneBridge());
    }

    public GrindExecutor(Minecraft client, InventoryService inventory, ScannerService scanner,
            RotationService rotations, BaritoneBridge baritone) {
        this.baritone = baritone;
        this.client = client;
        this.inventory = inventory;
        this.scanner = scanner;
        this.rotations = rotations;
    }

    /** Starts a complete execution plan derived from the same live inventory snapshot as the planner. */
    public StartResult start(String requestedTarget, int wanted) {
        String target = GrindBook.knows(requestedTarget) ? requestedTarget : GrindBook.generic(requestedTarget);
        if (!GrindBook.knows(target) || wanted < 1 || wanted > MAX_GOAL) return StartResult.INVALID;
        if (runner.running() || runner.state() == TaskRunner.State.NEEDS_MOVEMENT) return StartResult.BUSY;
        runner.cancel();
        releaseControls();
        stations.closeOwnedStationMenu();
        if (client == null || client.player == null || client.level == null || client.gameMode == null) {
            return StartResult.NO_WORLD;
        }

        survival = false;
        Map<String, Integer> have = planningInventory(target);
        try {
            if (CraftingPlan.plan(target, wanted, have, GrindBook.recipes()).isEmpty()) {
                runner.start(List.of());
                return StartResult.ALREADY_SATISFIED;
            }
            BlockPos[] nearby = stations.findNearbyStations();
            stations.craftingTablePos = nearby[0];
            stations.furnacePos = nearby[1];
            List<GrindExecutionPlan.Step> execution = GrindExecutionPlan.plan(target, wanted, have,
                    stations.craftingTablePos != null, stations.furnacePos != null);
            List<TaskRunner.Task> tasks = createTasks(execution, have);
            ownerPlayer = client.player;
            ownerLevel = client.level;
            stations.ownedStationMenuId = -1;
            runner.start(tasks);
            return StartResult.STARTED;
        } catch (RuntimeException failure) {
            ownerPlayer = null;
            ownerLevel = null;
            LOGGER.error("AutoGrind could not build an execution plan for {} x{}", target, wanted, failure);
            return StartResult.INVALID;
        }
    }

    public StartResult startSurvival(SurvivalProgression.Tier tier) {
        if (runner.running() || runner.state() == TaskRunner.State.NEEDS_MOVEMENT) return StartResult.BUSY;
        if (client.player == null || client.level == null || client.gameMode == null) return StartResult.NO_WORLD;
        if (client.level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return StartResult.INVALID;
        stop();
        survival = true;
        campaignReserves = Map.of();
        useBaritone = true;
        ownerPlayer = client.player;
        ownerLevel = client.level;
        if (survivalBase == null || homeWorld != client.level) {
            survivalBase = client.player.blockPosition().offset(2, 0, 0).immutable(); homeWorld = client.level;
            survivalStorage = List.of();
        }
        survivalTasks = new SurvivalTasks(this, tier, survivalBase);
        survivalTasks.storageChests = survivalStorage;
        runner.start(survivalTasks.tasks());
        return StartResult.STARTED;
    }
    public boolean baritoneAvailable() { return baritone.available(); }
    public void useBaritone(boolean enabled) { useBaritone = enabled; if (!enabled) travel.cancelAutomation(); }
    public boolean usingBaritone() { return useBaritone; }
    public String survivalTier() { return survival && survivalTasks != null ? survivalTasks.tier.name().toLowerCase(java.util.Locale.ROOT) : null; }

    /** Resume a task paused for direct reach, an open screen, or a missing free inventory slot. */
    public boolean resume() { return runner.resume(); }

    /** Advances one task tick on the client thread. Container clicks remain owned by InventoryService. */
    public void tick() {
        if (!runner.running() && runner.state() != TaskRunner.State.NEEDS_MOVEMENT) return;
        if (changingDimension) {
            if (client.player == null || client.level == null) { if (++transitionWait > 200) stop(); return; }
            if (client.level == ownerLevel) { if (++transitionWait > 200) stop(); return; }
            ownerLevel = client.level; ownerPlayer = client.player;
            if (client.level.dimension() == net.minecraft.world.level.Level.OVERWORLD) homeWorld = client.level;
            changingDimension = false; transitionWait = 0;
        }
        if (client == null || client.player == null || client.level == null
                || client.player != ownerPlayer || client.level != ownerLevel || !client.player.isAlive()) {
            stop();
            return;
        }
        if (!runner.running()) return;
        if (survival && survivalTasks.maintain()) return;
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
            travel.cancelAutomation();
            stations.closeOwnedStationMenu();
            ownerPlayer = null;
            ownerLevel = null;
        }
    }

    /** Cancels the current task and lets the shared transfer controller recover any carried stack. */
    public boolean stop() {
        boolean wasActive = runner.running() || runner.state() == TaskRunner.State.NEEDS_MOVEMENT;
        runner.cancel();
        if (survivalTasks != null) survivalTasks.cancel();
        travel.cancelAutomation();
        survival = false;
        changingDimension = false;
        releaseControls();
        inventory.transfers().release(OWNER);
        stations.closeOwnedStationMenu();
        ownerPlayer = null;
        ownerLevel = null;
        return wasActive;
    }

    public void reset() { stop(); survivalBase = null; homeWorld = null; survivalStorage = List.of(); protectedBlocks.clear(); }
    public boolean running() { return runner.running(); }
    public TaskRunner.State state() { return runner.state(); }
    public String currentTask() {
        return runner.running() && survival && survivalTasks != null && survivalTasks.storageWork != null
                ? "inventory recovery: " + survivalTasks.storageWork.currentTask() : runner.currentTask();
    }
    public int completed() { return runner.completed(); }
    public int total() { return runner.total(); }
    public String failure() { return runner.failure(); }
    public String blockedReason() { return runner.blockedReason(); }

    public void worldChanged() {
        if (survival && survivalTasks != null && survivalTasks.expectingDimension) {
            travel.cancelAutomation(); releaseControls(); inventory.transfers().release(OWNER);
            stations.ownedStationMenuId = -1; stations.craftingTablePos = null; stations.furnacePos = null;
            changingDimension = true; transitionWait = 0;
        } else reset();
    }
    public String baseCoordinates() { return survivalBase == null ? "unset" : coordinates(survivalBase); }
    void pause(String reason) { travel.cancelAutomation(); releaseControls(); inventory.transfers().release(OWNER); stations.closeOwnedStationMenu(); runner.pause(reason); }

    /** ClientLevel.hasChunk always returns true in 26.2; use the shared non-loading lookup. */
    boolean loaded(BlockPos pos) { return scanner.loadedChunk(pos.getX() >> 4, pos.getZ() >> 4) != null; }
    void protect(BlockPos pos) { protectedBlocks.computeIfAbsent(client.level.dimension(), ignored -> new java.util.HashSet<>()).add(pos.immutable()); }
    boolean protectedBlock(BlockPos pos) { return protectedBlocks.getOrDefault(client.level.dimension(), Set.of()).contains(pos); }
    void registerStorage(List<BlockPos> chests) {
        if (survival && survivalTasks != null) {
            survivalStorage = List.copyOf(chests);
            survivalTasks.storageChests = survivalStorage;
        }
    }

    Map<String, Integer> planningInventory(String target) {
        Map<String, Integer> have = carried();
        // A Silk Touch pickaxe cannot provide ore drops for prerequisite chains.
        for (int slot = 0; slot < InventoryTransfers.INVENTORY_SIZE; slot++) excludeSilkTool(have, target, inventory.stackAt(slot));
        excludeSilkTool(have, target, inventory.equipped(EquipmentSlot.OFFHAND));
        if (target.equals("oak_door") || target.equals("oak_planks") || target.equals("oak_log")) {
            // Exact wood and its generic alias describe the same stacks, never two supplies.
            have.merge(GrindBook.PLANKS, -have.getOrDefault("oak_planks", 0), Integer::sum);
            have.merge(GrindBook.LOG, -have.getOrDefault("oak_log", 0), Integer::sum);
        }
        return have;
    }
    private static void excludeSilkTool(Map<String, Integer> have, String target, ItemStack stack) {
        String item = SurvivalTasks.id(stack);
        if (!item.equals(target) && item.endsWith("_pickaxe") && usable(stack) && hasSilkTouch(stack))
            have.merge(item, -stack.getCount(), Integer::sum);
    }

    private void releaseControls() {
        rotations.release(OWNER);
        inventory.release(OWNER);
    }

    List<TaskRunner.Task> createTasks(List<GrindExecutionPlan.Step> plan,
            Map<String, Integer> startingInventory) {
        List<TaskRunner.Task> tasks = new ArrayList<>(plan.size());
        Map<String, Integer> simulated = new LinkedHashMap<>(startingInventory);
        for (int index = 0; index < plan.size(); index++) {
            GrindExecutionPlan.Step step = plan.get(index);
            GrindExecutionPlan.Step next = index + 1 < plan.size() ? plan.get(index + 1) : null;
            switch (step.action()) {
                case GATHER -> {
                    int goal = simulated.getOrDefault(step.item(), 0) + step.count();
                    tasks.add(step.item().equals("netherite_upgrade_smithing_template")
                            ? new SurvivalTasks.TemplateTask(this, goal)
                            : SurvivalTasks.hunted(step.item()) ? new SurvivalTasks.HuntTask(this, step.item(), goal)
                            : new GrindGatherTask(this, step.item(), goal));
                    simulated.merge(step.item(), step.count(), Integer::sum);
                }
                case CRAFT -> {
                    tasks.add(new GrindOffhandTask(this, ingredients(step.item())));
                    int goal = simulated.getOrDefault(step.item(), 0) + step.count();
                    tasks.add(new GrindCraftTask(this, step.item(), goal, step.needsTable()));
                    simulateRecipe(simulated, step.item(), step.count());
                }
                case PLACE_TABLE -> {
                    tasks.add(new GrindPlaceStationTask(this, Station.TABLE));
                    simulated.merge(GrindBook.CRAFTING_TABLE, -1, Integer::sum);
                }
                case OPEN_TABLE -> {
                    if (next != null && next.action() == GrindExecutionPlan.Action.CRAFT) {
                        tasks.add(new GrindOffhandTask(this, ingredients(next.item())));
                    }
                    tasks.add(new GrindOpenStationTask(this, Station.TABLE));
                }
                case PLACE_FURNACE -> {
                    tasks.add(new GrindPlaceStationTask(this, Station.FURNACE));
                    simulated.merge(GrindBook.FURNACE, -1, Integer::sum);
                }
                case OPEN_FURNACE -> {
                    tasks.add(new GrindOffhandTask(this, next == null ? Set.of(GrindBook.COAL) : ingredients(next.item())));
                    tasks.add(new GrindOpenStationTask(this, Station.FURNACE));
                }
                case SMITH -> {
                    int goal = simulated.getOrDefault(step.item(), 0) + step.count();
                    tasks.add(new SurvivalTasks.SmithTask(this, step.item(), goal));
                    simulateRecipe(simulated, step.item(), step.count());
                }
                case SMELT -> {
                    CraftingPlan.Recipe recipe = GrindBook.recipes().get(step.item());
                    int batches = step.count() / recipe.yield();
                    int rawIron = recipe.ingredients().getOrDefault(GrindBook.smeltInput(step.item()), 0) * batches;
                    int fuel = recipe.ingredients().getOrDefault(GrindBook.COAL, 0) * batches;
                    int goal = simulated.getOrDefault(step.item(), 0) + step.count();
                    tasks.add(new GrindSmeltTask(this, step.item(), goal, rawIron, fuel));
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
    int prepareHotbarItem(String generic) {
        int hotbar = findHotbarItem(generic);
        if (hotbar >= 0) return hotbar;
        int source = findCarriedSlot(generic);
        if (source < 0 && source != SOURCE_OFFHAND) return -1;
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

    int correctToolSlot(net.minecraft.world.level.block.state.BlockState state, String resource) {
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

    int findEmptyHotbarSlot() {
        for (int slot = 0; slot < InventoryTransfers.HOTBAR_SIZE; slot++) {
            if (inventory.stackAt(slot).isEmpty()) return slot;
        }
        return -1;
    }

    static boolean validTool(ItemStack stack, net.minecraft.world.level.block.state.BlockState state,
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
                || GrindBook.COAL.equals(resource) && (path.equals("coal_ore") || path.endsWith("_coal_ore"))
                || GrindBook.RAW_IRON.equals(resource) && (path.equals("iron_ore") || path.endsWith("_iron_ore"))
                || resource.equals("diamond") || resource.equals("lapis_lazuli") || resource.equals("raw_gold");
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

    String inventoryRecoveryReason() {
        return "Make the inventory menu available and free a slot so AutoGrind can return the carried stack, then run .grind resume.";
    }

    static boolean isTarget(String generic, net.minecraft.world.level.block.state.BlockState state) {
        if (GrindBook.LOG.equals(generic)) return state.is(BlockTags.LOGS);
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return switch (generic) {
            case GrindBook.COBBLESTONE -> path.equals("stone") || path.equals("cobblestone")
                    || path.equals("deepslate") || path.equals("cobbled_deepslate");
            case GrindBook.COAL -> path.equals("coal_ore") || path.endsWith("_coal_ore");
            case GrindBook.RAW_IRON -> path.equals("iron_ore") || path.endsWith("_iron_ore");
            case "diamond" -> path.equals("diamond_ore") || path.equals("deepslate_diamond_ore");
            case "raw_gold" -> path.equals("gold_ore") || path.equals("deepslate_gold_ore");
            case "ancient_debris", "obsidian", "oak_log", "netherrack", "sugar_cane" -> path.equals(generic);
            case "lapis_lazuli" -> path.equals("lapis_ore") || path.equals("deepslate_lapis_ore");
            case "flint" -> path.equals("gravel");
            case "wheat" -> state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(state);
            default -> false;
        };
    }

    static String coordinates(BlockPos pos) {
        return pos == null ? "unknown" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /** Counts through the shared inventory snapshot boundary rather than a second inventory path. */
    Map<String, Integer> carried() {
        Map<String, Integer> have = new LinkedHashMap<>();
        if (client == null || client.player == null) return have;
        for (int slot = 0; slot < InventoryTransfers.INVENTORY_SIZE; slot++) addStack(have, inventory.stackAt(slot));
        for (EquipmentSlot slot : NON_STORAGE_SLOTS) addStack(have, inventory.equipped(slot));
        return have;
    }

    int count(String generic) {
        if (client == null || client.player == null) return 0;
        int total = 0;
        for (int slot = 0; slot < InventoryTransfers.INVENTORY_SIZE; slot++) {
            total += countStack(inventory.stackAt(slot), generic);
        }
        for (EquipmentSlot slot : NON_STORAGE_SLOTS) total += countStack(inventory.equipped(slot), generic);
        return total;
    }

    private static void addStack(Map<String, Integer> have, ItemStack stack) {
        if (!usable(stack)) return;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String generic = GrindBook.generic(id);
        have.merge(generic, stack.getCount(), Integer::sum);
        String exact = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (!exact.equals(generic)) have.merge(exact, stack.getCount(), Integer::sum);
    }

    static int countStack(ItemStack stack, String generic) {
        return usable(stack)
                && (GrindBook.generic(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).equals(generic)
                || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(generic))
                ? stack.getCount() : 0;
    }
    static boolean usable(ItemStack stack) {
        return !stack.isEmpty() && (!stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > 1);
    }

    List<ContainerTransferController.Click> craftingInputClicks(String output, boolean table, int menuId) {
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
            } else if (wanted == null || countStack(placed, wanted) == 0) {
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

    boolean appendIngredientClicks(List<ContainerTransferController.Click> clicks, String ingredient,
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

    String resultItem(int menuId, String expected) {
        if (client.player == null || client.player.containerMenu == null
                || (menuId >= 0 && client.player.containerMenu.containerId != menuId)) return "";
        ItemStack output = client.player.containerMenu.getSlot(0).getItem();
        return countStack(output, expected) > 0 ? expected : "";
    }

}
