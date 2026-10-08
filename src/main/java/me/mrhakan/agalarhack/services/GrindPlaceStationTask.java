package me.mrhakan.agalarhack.services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Places a crafting table or furnace on a clear, supported spot near the player. Moved out of {@link GrindExecutor} in 2.0.04; it reaches the executor's shared state through {@code g}. */
final class GrindPlaceStationTask implements TaskRunner.Task {
    private final GrindExecutor g;

    private static final int PLACEMENT_CONFIRM_TICKS = 5;
    private final GrindExecutor.Station station;
    private int attempt;
    private int retryWait;
    private BlockPos pendingPlace;
    private boolean awaitingPlacement;
    private int placementWaitTicks;
    private int placementConfirmTicks;
    private boolean placementConfirmed;
    private int primedHotbar = -1;
    private boolean aimPrimed;
    private String placementDiagnostic = "no interaction issued";
    private String movementReason;
    private String failureReason;

    GrindPlaceStationTask(GrindExecutor g, GrindExecutor.Station station) {
        this.g = g;
        this.station = station;
    }
    @Override public String name() { return "place " + station.item; }
    @Override public boolean satisfied() {
        return placementConfirmed;
    }

    @Override public boolean tick() {
        movementReason = null;
        if (failureReason != null) return false;
        if (awaitingPlacement) {
            // Keep the selected hand and visible aim authoritative until the server either
            // confirms the block or the bounded acknowledgement window expires.
            if (primedHotbar >= 0) g.inventory.select(GrindExecutor.OWNER, GrindExecutor.PRIORITY, primedHotbar, false, true);
            if (pendingPlace != null) g.reach.aimAt(Vec3.atBottomCenterOf(pendingPlace));
        }
        BlockPos observed = g.stations.stationPosition(station);
        if (observed != null && g.client.level.getBlockState(observed).is(station.block)) {
            // Vanilla predicts a placement on the client before the integrated/remote server
            // accepts it. Requiring a short stable observation keeps the following open task
            // from advancing into a block that a late server correction has already rejected.
            if (++placementConfirmTicks >= PLACEMENT_CONFIRM_TICKS) {
                placementConfirmed = true;
                g.inventory.release(GrindExecutor.OWNER);
                g.rotations.release(GrindExecutor.OWNER);
            }
            return true;
        }
        placementConfirmTicks = 0;
        if (g.stations.closeOwnedStationMenu()) return true;
        if (g.client.gui.screen() != null || g.client.player.containerMenu != g.client.player.inventoryMenu) {
            movementReason = "Close the open screen before AutoGrind can place " + station.item + ".";
            return true;
        }
        if (awaitingPlacement) {
            if (++placementWaitTicks <= 40) return true;
            // The interaction result records the clicked face, while a replaceable block or
            // a server-side placement rule can still choose a nearby position. Reconcile once
            // after the acknowledgement window before consuming another station item.
            BlockPos nearby = g.reach.findNearbyBlock(station.block);
            if (nearby != null) {
                g.stations.stationPosition(station, nearby);
                placementConfirmTicks = 1;
                return true;
            }
            awaitingPlacement = false;
            placementWaitTicks = 0;
            attempt++;
            pendingPlace = null;
            retryWait = 5;
            primedHotbar = -1;
            aimPrimed = false;
            g.inventory.release(GrindExecutor.OWNER);
            g.rotations.release(GrindExecutor.OWNER);
            return true;
        }
        if (retryWait > 0) { retryWait--; return true; }
        if (attempt >= 4) {
            failureReason = "could not place " + station.item + " on a nearby clear solid surface";
            return false;
        }
        int hotbar = g.prepareHotbarItem(station.item);
        if (hotbar == GrindExecutor.HOTBAR_PENDING) {
            if (g.inventory.transfers().recoveryBlocked()) movementReason = g.inventoryRecoveryReason();
            return true;
        }
        if (hotbar < 0) {
            failureReason = "the " + station.item + " is not in the inventory; last placement: "
                    + placementDiagnostic;
            return false;
        }
        if (pendingPlace == null) pendingPlace = placementCandidate(attempt);
        if (pendingPlace == null) {
            movementReason = "Stand near a clear solid surface to place " + station.item + ".";
            return true;
        }
        BlockPos place = pendingPlace;
        BlockPos support = place.below();
        if (!g.reach.withinReach(support)) {
            movementReason = "Move within reach of the surface at " + GrindExecutor.coordinates(support)
                    + " to place " + station.item + ".";
            return true;
        }
        if (!g.inventory.select(GrindExecutor.OWNER, GrindExecutor.PRIORITY, hotbar, false, true)) {
            primedHotbar = -1;
            return true;
        }
        // Give vanilla one client tick to publish the leased selected slot before issuing the
        // use interaction. Other inventory leases often act through a later key tick; station
        // placement is immediate and otherwise risks the server evaluating the previous hand.
        if (primedHotbar != hotbar) {
            primedHotbar = hotbar;
            return true;
        }
        Vec3 placementAim = Vec3.atBottomCenterOf(place);
        g.reach.aimAt(placementAim);
        if (!g.reach.aimedAt(placementAim)) {
            aimPrimed = false;
            return true;
        }
        // RotationService resolves after this task. Keep the visible vanilla rotation for a
        // complete network tick before use so the server validates the same face the client hit.
        if (!aimPrimed) {
            aimPrimed = true;
            return true;
        }
        BlockHitResult hit = g.reach.placementHit(support);
        if (hit == null || g.client.gameMode == null) {
            attempt++;
            pendingPlace = null;
            retryWait = 4;
            primedHotbar = -1;
            aimPrimed = false;
            g.inventory.release(GrindExecutor.OWNER);
            g.rotations.release(GrindExecutor.OWNER);
            return true;
        }
        ItemStack heldBefore = g.client.player.getItemInHand(InteractionHand.MAIN_HAND).copy();
        var interaction = g.client.gameMode.useItemOn(g.client.player, InteractionHand.MAIN_HAND, hit);
        BlockPos predicted = hit.getBlockPos().relative(hit.getDirection());
        g.stations.stationPosition(station, predicted);
        placementDiagnostic = "result=" + interaction + ", centered-hit, held="
                + BuiltInRegistries.ITEM.getKey(heldBefore.getItem()) + "x" + heldBefore.getCount()
                + ", selected=" + hotbar + ", hit=" + GrindExecutor.coordinates(hit.getBlockPos())
                + ", face=" + hit.getDirection() + ", predicted=" + GrindExecutor.coordinates(predicted);
        awaitingPlacement = true;
        placementWaitTicks = 0;
        aimPrimed = false;
        return true;
    }

    private BlockPos placementCandidate(int index) {
        Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        BlockPos feet = g.client.player.blockPosition();
        for (int offset = 0; offset < sides.length; offset++) {
            BlockPos pos = feet.relative(sides[(index + offset) % sides.length]);
            BlockPos support = pos.below();
            LevelChunk chunk = g.scanner.loadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null || !chunk.getBlockState(pos).isAir()) continue;
            if (g.client.level.getBlockState(support).getCollisionShape(g.client.level, support).isEmpty()) continue;
            if (g.reach.withinReach(support)) return pos.immutable();
        }
        return null;
    }

    @Override public String blockedReason() { return movementReason; }
    @Override public String failureReason() { return failureReason; }
    @Override public void cancel() { g.inventory.release(GrindExecutor.OWNER); g.rotations.release(GrindExecutor.OWNER); g.inventory.transfers().release(GrindExecutor.OWNER); }
}
