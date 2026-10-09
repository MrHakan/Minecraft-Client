package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

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
    static final int HOTBAR_PENDING = -2;

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
    /** True while the base is the unsearched default because no plot fit; a restart searches again. */
    private boolean provisionalBase;
    private ClientLevel homeWorld;
    private final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Set<BlockPos>> protectedBlocks = new LinkedHashMap<>();
    private final TaskRunner runner = new TaskRunner();
    private LocalPlayer ownerPlayer;
    private ClientLevel ownerLevel;
    final GrindTravel travel = new GrindTravel(this);
    final GrindStations stations = new GrindStations(this);
    final GrindReach reach = new GrindReach(this);
    final GrindItems items = new GrindItems(this);
    final GrindStorage storage = new GrindStorage(this);

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
        Map<String, Integer> have = items.planningInventory(target);
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
        String plotProblem = null;
        boolean newWorld = survivalBase == null || homeWorld != client.level;
        if (newWorld || provisionalBase) {
            BlockPos fallback = client.player.blockPosition().offset(2, 0, 0).immutable();
            BlockPos plot = choosePlot(tier, fallback);
            survivalBase = plot != null ? plot : fallback; homeWorld = client.level;
            provisionalBase = plot == null;
            if (newWorld) storage.clear();
            if (plot == null) {
                plotProblem = "No clear, level spot within " + GrindPlot.SEARCH_RADIUS + " blocks for the "
                        + tier.name().toLowerCase(java.util.Locale.ROOT) + " campaign's buildings; move to open, level ground and start again, or .grind resume to build at "
                        + coordinates(fallback) + " anyway.";
            }
        }
        survivalTasks = new SurvivalTasks(this, tier, survivalBase);
        runner.start(survivalTasks.tasks());
        if (plotProblem != null) runner.pause(plotProblem);
        return StartResult.STARTED;
    }

    /** The nearest plot that fits the tier's buildings, or {@code null}; reads loaded chunks only, once. */
    private BlockPos choosePlot(SurvivalProgression.Tier tier, BlockPos fallback) {
        long started = System.nanoTime();
        LoadedTerrain terrain = new LoadedTerrain(fallback.getY());
        var plot = GrindPlot.choose(terrain, SurvivalBlueprint.footprint(tier), fallback.getX(), fallback.getY(), fallback.getZ());
        LOGGER.info("AutoGrind build plot search: {} after reading {} columns in {} ms", plot == null ? "none" : plot,
                terrain.columns.size(), (System.nanoTime() - started) / 1_000_000);
        return plot == null ? null : new BlockPos(plot.x(), plot.y(), plot.z());
    }

    /** Column tops from loaded chunks, each read once per search. */
    private final class LoadedTerrain implements GrindPlot.Terrain {
        final Map<Long, int[]> columns = new HashMap<>();
        private final int feetY;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        LoadedTerrain(int feetY) { this.feetY = feetY; }

        public int top(int x, int z) { return column(x, z)[0]; }
        public boolean solid(int x, int z) { return column(x, z)[1] != 0; }

        private int[] column(int x, int z) {
            return columns.computeIfAbsent(BlockPos.asLong(x, 0, z), key -> scan(x, z));
        }

        private int[] scan(int x, int z) {
            LevelChunk chunk = scanner.loadedChunk(x >> 4, z >> 4);
            if (chunk == null) return new int[]{GrindPlot.UNKNOWN, 0};
            int top = Math.min(feetY + GrindPlot.SCAN_TOP, client.level.getMaxY());
            int bottom = Math.max(feetY + GrindPlot.SCAN_BOTTOM, client.level.getMinY());
            for (int y = top; y >= bottom; y--) {
                BlockState state = chunk.getBlockState(cursor.set(x, y, z));
                boolean fluid = !state.getFluidState().isEmpty();
                if (state.canBeReplaced() && !fluid) continue;
                boolean solid = !fluid && !state.getCollisionShape(client.level, cursor).isEmpty();
                return new int[]{y, solid ? 1 : 0};
            }
            return new int[]{GrindPlot.UNKNOWN, 0};
        }
    }
    public boolean baritoneAvailable() { return baritone.available(); }
    public void useBaritone(boolean enabled) { useBaritone = enabled; if (!enabled) travel.cancelAutomation(); }
    public boolean usingBaritone() { return useBaritone; }
    public String survivalTier() { return survival && survivalTasks != null ? survivalTasks.tier.name().toLowerCase(java.util.Locale.ROOT) : null; }

    /** Resume a task paused for direct reach, an open screen, or a missing free inventory slot. */
    public boolean resume() {
        boolean resumed = runner.resume();
        // Resuming the no-plot pause accepts the default corner; a later restart must not move it.
        if (resumed) provisionalBase = false;
        return resumed;
    }

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

    public void reset() { stop(); survivalBase = null; provisionalBase = false; homeWorld = null; storage.clear(); protectedBlocks.clear(); }
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
        if (survival && survivalTasks != null) storage.register(chests);
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

}
