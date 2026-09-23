package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.mrhakan.agalarhack.services.scanning.BlockScanCursor;
import me.mrhakan.agalarhack.services.scanning.ScanScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Executes the first safe slice of a crafting plan: raw wood that is already loaded and within the
 * player's normal interaction reach. Planning remains in {@link CraftingPlan}; this class turns
 * only its gather step into a bounded scan, vanilla block-break interaction and inventory check.
 *
 * <p>Movement and crafting are deliberately out of scope until a 26.2-compatible backend has been
 * verified. A found target outside reach pauses the task with its coordinates instead of claiming
 * it can path to the block.
 */
public final class GrindExecutor {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("agalarhack-autogrind");
    private static final int MAX_GOAL = 512;
    private static final int SCAN_HORIZONTAL = 6;
    private static final int SCAN_VERTICAL = 4;
    private static final int SCAN_STEPS_PER_TICK = 512;
    private static final double MAX_REACH = 4.5;
    private static final int PICKUP_GRACE_TICKS = 60;
    private static final double PICKUP_HORIZONTAL_REACH = 1.75;
    private static final double PICKUP_VERTICAL_REACH = 2.5;
    private static final EquipmentSlot[] NON_STORAGE_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.OFFHAND
    };

    public enum StartResult {
        STARTED,
        ALREADY_SATISFIED,
        BUSY,
        NO_WORLD,
        UNSUPPORTED,
        INVALID
    }

    private final Minecraft client;
    private final InventoryService inventory;
    private final ScannerService scanner;
    private final RotationService rotations;
    private final TaskRunner runner = new TaskRunner();
    private LocalPlayer ownerPlayer;
    private ClientLevel ownerLevel;

    public GrindExecutor(Minecraft client, InventoryService inventory, ScannerService scanner,
            RotationService rotations) {
        this.client = client;
        this.inventory = inventory;
        this.scanner = scanner;
        this.rotations = rotations;
    }

    /** Starts only raw log gathering; crafted plans remain inspectable, but never half-execute. */
    public StartResult start(String requestedTarget, int wanted) {
        String target = GrindBook.generic(requestedTarget);
        if (!GrindBook.knows(target) || wanted < 1 || wanted > MAX_GOAL) return StartResult.INVALID;
        if (runner.running() || runner.state() == TaskRunner.State.NEEDS_MOVEMENT) return StartResult.BUSY;
        // A new request replaces old terminal progress so status cannot say "complete" for a
        // previous goal after the current goal was refused.
        runner.cancel();
        if (client == null || client.player == null || client.level == null || client.gameMode == null) {
            return StartResult.NO_WORLD;
        }

        Map<String, Integer> have = carried();
        List<CraftingPlan.Step> steps;
        try {
            steps = CraftingPlan.plan(target, wanted, have, GrindBook.recipes());
        } catch (RuntimeException failure) {
            LOGGER.error("AutoGrind could not plan {} x{}", target, wanted, failure);
            return StartResult.INVALID;
        }
        if (steps.isEmpty()) {
            runner.start(List.of());
            return StartResult.ALREADY_SATISFIED;
        }
        for (CraftingPlan.Step step : steps) {
            if (step.kind() != CraftingPlan.Kind.GATHER || !GrindBook.LOG.equals(step.item())) {
                return StartResult.UNSUPPORTED;
            }
        }

        List<TaskRunner.Task> tasks = new ArrayList<>(steps.size());
        for (CraftingPlan.Step step : steps) {
            int absoluteGoal = have.getOrDefault(step.item(), 0) + step.count();
            tasks.add(new GatherLogsTask(absoluteGoal));
        }
        ownerPlayer = client.player;
        ownerLevel = client.level;
        runner.start(tasks);
        return StartResult.STARTED;
    }

    /** Resume a movement-paused task after the player has moved closer to its reported target. */
    public boolean resume() { return runner.resume(); }

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
        TaskRunner.State state = runner.state();
        if (state == TaskRunner.State.NEEDS_MOVEMENT) {
            // A paused task owns no player controls. The player needs to be free to look and move
            // before deciding when to resume it.
            releaseControls();
        } else if (state == TaskRunner.State.DONE || state == TaskRunner.State.FAILED) {
            releaseControls();
            ownerPlayer = null;
            ownerLevel = null;
        }
    }

    /** Cancels the current task and any vanilla block-break interaction it owns. */
    public boolean stop() {
        boolean wasActive = runner.running() || runner.state() == TaskRunner.State.NEEDS_MOVEMENT;
        runner.cancel();
        releaseControls();
        ownerPlayer = null;
        ownerLevel = null;
        return wasActive;
    }

    /** Lifecycle alias used by world and connection cleanup. */
    public void reset() { stop(); }

    public boolean running() { return runner.running(); }
    public TaskRunner.State state() { return runner.state(); }
    public String currentTask() { return runner.currentTask(); }
    public int completed() { return runner.completed(); }
    public int total() { return runner.total(); }
    public String failure() { return runner.failure(); }
    public String blockedReason() { return runner.blockedReason(); }

    private void releaseControls() {
        rotations.release("autogrind");
    }

    private final class GatherLogsTask implements TaskRunner.Task {
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

        private GatherLogsTask(int goal) { this.goal = goal; }

        @Override public String name() { return "gather logs (" + count(GrindBook.LOG) + "/" + goal + ")"; }

        @Override public boolean satisfied() { return count(GrindBook.LOG) >= goal; }

        @Override
        public boolean tick() {
            movementReason = null;
            if (failureReason != null) return false;
            if (satisfied()) return true;
            if (client.gui.screen() != null) {
                movementReason = "Close the open screen before AutoGrind can break a log.";
                return true;
            }

            if (target != null) {
                if (!isLog(client.level.getBlockState(target))) {
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
                        inventoryBeforeMining = count(GrindBook.LOG);
                        client.gameMode.startDestroyBlock(target, hit.getDirection());
                        mining = true;
                    } else {
                        client.gameMode.continueDestroyBlock(target, hit.getDirection());
                    }
                    return true;
                }
            }

            if (waitingForPickup) {
                if (count(GrindBook.LOG) > inventoryBeforeMining) {
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
                    failureReason = "found no log block in loaded chunks within " + SCAN_HORIZONTAL
                            + " blocks; no block was broken";
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
                failureReason = "log scan failed: " + failure.getMessage();
                LOGGER.error("AutoGrind log scan failed", failure);
                return ScanScheduler.Result.DONE;
            }
        }

        private ScanScheduler.Result scanStepBounded(ScanScheduler.Budget budget) {
            if (cursor.cycle() > 0) {
                scanReady = true;
                return ScanScheduler.Result.DONE;
            }
            int x = cursor.x(), y = cursor.y(), z = cursor.z();
            if (!budgetAllowsBlock(budget, x, z)) return ScanScheduler.Result.BLOCKED;
            LevelChunk chunk = scanner.loadedChunk(x >> 4, z >> 4);
            if (chunk == null) {
                cursor.skipChunk();
                if (cursor.cycle() > 0) {
                    scanReady = true;
                    return ScanScheduler.Result.DONE;
                }
                return ScanScheduler.Result.MORE;
            }
            if (y >= client.level.getMinY() && y < client.level.getMaxY()) {
                BlockPos pos = new BlockPos(x, y, z);
                if (isLog(chunk.getBlockState(pos))) consider(pos);
            }
            cursor.advance();
            if (cursor.cycle() > 0) {
                scanReady = true;
                return ScanScheduler.Result.DONE;
            }
            return ScanScheduler.Result.MORE;
        }

        private boolean budgetAllowsBlock(ScanScheduler.Budget budget, int x, int z) {
            return scanner.reserveBlock(budget, x, z);
        }

        private void consider(BlockPos pos) {
            Vec3 eye = client.player.getEyePosition();
            double distance = eye.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < nearestOverallDistance) {
                nearestOverallDistance = distance;
                nearestOverall = pos.immutable();
            }
            // Visibility depends on the view rotation. Do not test the ray against the player's
            // old look direction here: the executor owns a RotationService request that first
            // turns toward this candidate, after which tick() verifies the actual ray hit.
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

        private boolean withinReach(BlockPos pos) {
            return client.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_REACH * MAX_REACH;
        }

        private boolean nearPickupPosition() {
            if (pickupPosition == null || client.player == null) return false;
            Vec3 player = client.player.position();
            Vec3 drop = Vec3.atCenterOf(pickupPosition);
            double x = player.x - drop.x;
            double z = player.z - drop.z;
            return x * x + z * z <= PICKUP_HORIZONTAL_REACH * PICKUP_HORIZONTAL_REACH
                    && Math.abs(player.y - drop.y) <= PICKUP_VERTICAL_REACH;
        }

        private String pickupMovementReason() {
            return "The log drop at " + coordinates(pickupPosition)
                    + " is not in your inventory; move over it and run .grind resume.";
        }

        private BlockHitResult hitTarget(BlockPos pos) {
            Vec3 from = client.player.getEyePosition();
            Vec3 to = Vec3.atCenterOf(pos);
            var hit = client.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, client.player));
            return hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos)
                    ? blockHit : null;
        }

        private void aimAt(BlockPos pos) {
            Vec3 delta = Vec3.atCenterOf(pos).subtract(client.player.getEyePosition());
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float yaw = (float) (Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horizontal)));
            rotations.request(new RotationService.Request("autogrind", 50, RotationService.Mode.CLIENT,
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

        private String movementMessage(BlockPos pos, String reason) {
            return "Target log at " + coordinates(pos) + " — " + reason
                    + "; movement automation unavailable without a verified 26.2 pathing backend.";
        }

        @Override public String blockedReason() { return movementReason; }
        @Override public String failureReason() { return failureReason; }

        @Override
        public void cancel() {
            scanner.cancel(this);
            stopBreaking();
            rotations.release("autogrind");
        }

        private void stopBreaking() {
            if (mining && client.gameMode != null) client.gameMode.stopDestroyBlock();
            mining = false;
        }
    }

    private static boolean isLog(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(BlockTags.LOGS);
    }

    private static String coordinates(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /** Counts through the shared inventory snapshot boundary rather than a second inventory path. */
    private Map<String, Integer> carried() {
        Map<String, Integer> have = new LinkedHashMap<>();
        if (client == null || client.player == null) return have;
        for (int slot = 0; slot < InventoryTransfers.INVENTORY_SIZE; slot++) {
            addStack(have, inventory.stackAt(slot));
        }
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
}
