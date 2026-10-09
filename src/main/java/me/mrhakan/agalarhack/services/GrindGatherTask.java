package me.mrhakan.agalarhack.services;

import java.util.Set;
import me.mrhakan.agalarhack.services.scanning.BlockScanCursor;
import me.mrhakan.agalarhack.services.scanning.ScanScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Gathers a raw resource: scans for the nearest matching block, travels or asks for movement, breaks it and waits for the drop. Moved out of {@link GrindExecutor} in 2.0.04; it reaches the executor's shared state through {@code g}. */
final class GrindGatherTask implements TaskRunner.Task {
    private final GrindExecutor g;

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

    GrindGatherTask(GrindExecutor g, String resource, int goal) {
        this.g = g;
        this.resource = resource; this.goal = goal;
    }
    @Override public String name() { return "gather " + resource + " (" + g.items.count(resource) + "/" + goal + ")"; }
    @Override public boolean satisfied() {
        if (g.items.count(resource) < goal) return false;
        g.travel.cancelOwnedMining();
        g.travel.cancelMovement();
        return true;
    }
    @Override public int budgetTicks() { return Math.max(TaskRunner.DEFAULT_BUDGET_TICKS, Math.min(70_000, (goal - g.items.count(resource)) * 100 + 4_000)); }

    @Override
    public boolean tick() {
        movementReason = null;
        if (failureReason != null) return false;
        if (satisfied()) return true;
        if (g.stations.closeOwnedStationMenu()) return true;
        if (g.client.gui.screen() != null) {
            movementReason = "Close the open screen before AutoGrind can gather " + resource + ".";
            return true;
        }

        if (resource.equals("netherite_upgrade_smithing_template")) {
            movementReason = "Find Netherite Upgrade templates in bastion loot (need " + goal
                    + "). AutoGrind cannot infer unseen loot; collect them, then .grind resume.";
            return true;
        }
        if (resource.equals("ancient_debris") && g.client.level.dimension() != net.minecraft.world.level.Level.NETHER) {
            movementReason = "Enter the Nether to gather ancient debris, then .grind resume.";
            return true;
        }
        if (resource.equals("flint") && g.useBaritone && g.baritone.available()) {
            // Gravel drops flint probabilistically; live flint count is the completion signal.
            return tickBaritone(new String[]{"gravel"});
        }
        String[] names = GrindBook.baritoneNames(resource);
        if (g.useBaritone && g.baritone.available() && names.length > 0) {
            return tickBaritone(names);
        }

        if (target != null) {
            if (!GrindExecutor.isTarget(resource, g.client.level.getBlockState(target))) {
                stopBreaking();
                pickupPosition = target.immutable();
                target = null;
                waitingForPickup = true;
                pickupWait = 0;
            } else {
                if (!g.reach.withinReach(target)) {
                    movementReason = movementMessage(target, "outside direct interaction reach");
                    return true;
                }
                int toolSlot = -1;
                if (!Set.of(GrindBook.LOG, "oak_log", "sugar_cane", "wheat", "flint").contains(resource)) {
                toolSlot = g.items.correctToolSlot(g.client.level.getBlockState(target), resource);
                    if (toolSlot == GrindExecutor.HOTBAR_PENDING) {
                        if (g.inventory.transfers().recoveryBlocked()) movementReason = g.items.inventoryRecoveryReason();
                        return true;
                    }
                    if (toolSlot < 0) {
                        failureReason = "no carried pickaxe can harvest the " + resource + " at " + GrindExecutor.coordinates(target);
                        return false;
                    }
                    if (!g.inventory.select(GrindExecutor.OWNER, GrindExecutor.PRIORITY, toolSlot, false, true)) return true;
                }
                g.reach.aimAt(target);
                if (!g.reach.aimedAt(target)) return true;
                BlockHitResult hit = g.reach.hitTarget(target);
                if (hit == null) {
                    movementReason = movementMessage(target, "blocked from direct interaction");
                    return true;
                }
                if (g.client.gameMode == null) {
                    failureReason = "lost the active game mode while breaking " + GrindExecutor.coordinates(target);
                    return false;
                }
                if (!mining) {
                    inventoryBeforeMining = g.items.count(resource);
                    g.client.gameMode.startDestroyBlock(target, hit.getDirection());
                    mining = true;
                } else {
                    g.client.gameMode.continueDestroyBlock(target, hit.getDirection());
                }
                return true;
            }
        }

        if (waitingForPickup && resource.equals("flint") && pickupWait >= 20 && nearPickupPosition()) {
            waitingForPickup = false; scanStarted = false; pickupPosition = null;
        }
        if (waitingForPickup) {
            if (g.items.count(resource) > inventoryBeforeMining) {
                waitingForPickup = false;
                pickupPosition = null;
                pickupWait = 0;
            } else {
                if (pickupWait >= GrindExecutor.PICKUP_GRACE_TICKS) {
                    if (nearPickupPosition()) pickupWait = 0;
                    else {
                        movementReason = pickupMovementReason();
                        return true;
                    }
                }
                if (++pickupWait < GrindExecutor.PICKUP_GRACE_TICKS) return true;
                movementReason = pickupMovementReason();
                return true;
            }
        }

        if (scanReady) {
            scanReady = false;
            target = nearestInReach != null ? nearestInReach : nearestOverall;
            if (target == null) {
                movementReason = "found no " + resource + " block in loaded chunks within "
                        + GrindExecutor.SCAN_HORIZONTAL + " blocks; move to resources or enable Baritone, then .grind resume.";
                scanStarted = false;
                return true;
            }
            if (!g.reach.withinReach(target)) {
                movementReason = movementMessage(target,
                        "movement automation unavailable; move closer and run .grind resume");
                return true;
            }
        } else {
            prepareScan();
            g.scanner.offerClientTask(this, ScanScheduler.Priority.NEAR, GrindExecutor.SCAN_STEPS_PER_TICK, scanTask);
        }
        return true;
    }

    private boolean tickBaritone(String[] names) {
            g.scanner.cancel(this);
            g.rotations.release(GrindExecutor.OWNER); g.inventory.release(GrindExecutor.OWNER);
            if (GrindExecutionPlan.toolTierFor(resource) > 0) {
                var state = switch (resource) {
                    case "cobblestone" -> Blocks.STONE.defaultBlockState();
                    case "coal" -> Blocks.COAL_ORE.defaultBlockState();
                    case "raw_iron" -> Blocks.IRON_ORE.defaultBlockState();
                    case "diamond" -> Blocks.DIAMOND_ORE.defaultBlockState();
                    case "raw_gold" -> Blocks.GOLD_ORE.defaultBlockState();
                    case "lapis_lazuli" -> Blocks.LAPIS_ORE.defaultBlockState();
                    case "ancient_debris" -> Blocks.ANCIENT_DEBRIS.defaultBlockState();
                    default -> Blocks.OBSIDIAN.defaultBlockState();
                };
                boolean tool = GrindItems.validTool(g.inventory.equipped(EquipmentSlot.OFFHAND), state, resource);
                for (int slot = 0; slot < InventoryTransfers.INVENTORY_SIZE && !tool; slot++)
                    tool = GrindItems.validTool(g.inventory.stackAt(slot), state, resource);
                if (!tool) {
                    g.travel.cancelAutomation();
                    failureReason = "no carried pickaxe can harvest " + resource;
                    return false;
                }
            }
            if (!g.travel.ownsMining) {
                if (g.travel.awaitCancellation()) return true;
                if (g.baritone.mining() || g.baritone.pathing() || g.baritone.goalActive()) {
                    movementReason = "Another Baritone process is active; stop it before .grind resume.";
                    return true;
                }
                if (g.baritone.mineByName(0, names) != BaritoneBridge.Result.STARTED) {
                    movementReason = "Baritone could not start " + resource + " mining; inspect its log or disable it.";
                    return true;
                }
                g.travel.ownsMining = true;
            } else if (!g.baritone.mining()) {
                g.travel.ownsMining = false;
                movementReason = "Baritone ended mining before " + resource + " reached its inventory goal; .grind resume to retry.";
            }
            return true;
    }

    private ScanScheduler.Result scanStep(ScanScheduler.Budget budget) {
        try {
            return scanStepBounded(budget);
        } catch (RuntimeException failure) {
            failureReason = resource + " scan failed: " + failure.getMessage();
            GrindExecutor.LOGGER.error("AutoGrind {} scan failed", resource, failure);
            return ScanScheduler.Result.DONE;
        }
    }

    private ScanScheduler.Result scanStepBounded(ScanScheduler.Budget budget) {
        if (cursor.cycle() > 0) { scanReady = true; return ScanScheduler.Result.DONE; }
        int x = cursor.x(), y = cursor.y(), z = cursor.z();
        if (!g.scanner.reserveBlock(budget, x, z)) return ScanScheduler.Result.BLOCKED;
        LevelChunk chunk = g.scanner.loadedChunk(x >> 4, z >> 4);
        if (chunk == null) {
            cursor.skipChunk();
            if (cursor.cycle() > 0) { scanReady = true; return ScanScheduler.Result.DONE; }
            return ScanScheduler.Result.MORE;
        }
        if (y >= g.client.level.getMinY() && y < g.client.level.getMaxY()) {
            BlockPos pos = new BlockPos(x, y, z);
            if (GrindExecutor.isTarget(resource, chunk.getBlockState(pos))) consider(pos);
        }
        cursor.advance();
        if (cursor.cycle() > 0) { scanReady = true; return ScanScheduler.Result.DONE; }
        return ScanScheduler.Result.MORE;
    }

    private void consider(BlockPos pos) {
        if (g.protectedBlock(pos)) return;
        double distance = g.client.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
        if (distance < nearestOverallDistance) {
            nearestOverallDistance = distance;
            nearestOverall = pos.immutable();
        }
        // RotationService turns before the executor verifies the actual ray hit.
        if (distance <= GrindExecutor.MAX_REACH * GrindExecutor.MAX_REACH && distance < nearestInReachDistance) {
            nearestInReachDistance = distance;
            nearestInReach = pos.immutable();
        }
    }

    private void prepareScan() {
        BlockPos center = g.client.player.blockPosition();
        if (scanStarted && Math.abs(center.getX() - scanCenterX) <= 1
                && Math.abs(center.getY() - scanCenterY) <= 1
                && Math.abs(center.getZ() - scanCenterZ) <= 1) return;
        scanCenterX = center.getX(); scanCenterY = center.getY(); scanCenterZ = center.getZ();
        cursor.reset(scanCenterX, scanCenterY, scanCenterZ, GrindExecutor.SCAN_HORIZONTAL, GrindExecutor.SCAN_VERTICAL);
        nearestInReach = null;
        nearestOverall = null;
        nearestInReachDistance = Double.MAX_VALUE;
        nearestOverallDistance = Double.MAX_VALUE;
        scanStarted = true;
    }

    private boolean nearPickupPosition() {
        if (pickupPosition == null || g.client.player == null) return false;
        Vec3 player = g.client.player.position(), drop = Vec3.atCenterOf(pickupPosition);
        double x = player.x - drop.x, z = player.z - drop.z;
        return x * x + z * z <= GrindExecutor.PICKUP_HORIZONTAL_REACH * GrindExecutor.PICKUP_HORIZONTAL_REACH
                && Math.abs(player.y - drop.y) <= GrindExecutor.PICKUP_VERTICAL_REACH;
    }

    private String pickupMovementReason() {
        return "The " + resource + " drop at " + GrindExecutor.coordinates(pickupPosition)
                + " is not in your inventory; move over it and run .grind resume.";
    }

    private String movementMessage(BlockPos pos, String reason) {
        return "Target " + resource + " block at " + GrindExecutor.coordinates(pos) + " — " + reason
                + "; movement automation unavailable without a verified 26.2 pathing backend.";
    }

    @Override public String blockedReason() { return movementReason; }
    @Override public String failureReason() { return failureReason; }

    @Override
    public void cancel() {
        g.scanner.cancel(this);
        g.travel.cancelOwnedMining();
        g.travel.cancelMovement();
        stopBreaking();
        g.rotations.release(GrindExecutor.OWNER);
        g.inventory.release(GrindExecutor.OWNER);
    }

    private void stopBreaking() {
        if (mining && g.client.gameMode != null) g.client.gameMode.stopDestroyBlock();
        mining = false;
        g.inventory.release(GrindExecutor.OWNER);
    }
}
