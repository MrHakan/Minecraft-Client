package me.mrhakan.agalarhack.services;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;

/** Opens a placed crafting table or furnace, placing a portable one first when none is in reach. Moved out of {@link GrindExecutor} in 2.0.04; it reaches the executor's shared state through {@code g}. */
final class GrindOpenStationTask implements TaskRunner.Task {
    private final GrindExecutor g;

    private final GrindExecutor.Station station;
    private boolean interactionIssued;
    private SurvivalTasks.ItemGoalTask portableSupply;
    private GrindPlaceStationTask portablePlacement;
    private int waitTicks;
    private String movementReason;
    private String failureReason;

    GrindOpenStationTask(GrindExecutor g, GrindExecutor.Station station) {
        this.g = g;
        this.station = station;
    }
    @Override public String name() { return "open " + station.item; }

    @Override public boolean satisfied() {
        if (!g.stationMenuOpen(station)) return false;
        if (interactionIssued) g.ownedStationMenuId = g.client.player.containerMenu.containerId;
        return true;
    }

    @Override public boolean tick() {
        movementReason = null;
        if (failureReason != null) return false;
        if (satisfied()) return true;
        if (g.client.gui.screen() != null) {
            if (g.closeOwnedStationMenu()) return true;
            movementReason = "Close the open screen before AutoGrind can open " + station.item + ".";
            return true;
        }
        if (g.client.player.containerMenu != g.client.player.inventoryMenu) return true;
        if (interactionIssued) {
            if (++waitTicks > 40) {
                failureReason = "vanilla did not open the " + station.item + " menu";
                return false;
            }
            return true;
        }
        BlockPos pos = g.stationPosition(station);
        if (pos == null || !g.client.level.getBlockState(pos).is(station.block)) {
            pos = g.findNearbyBlock(station.block);
            g.stationPosition(station, pos);
        }
        if (pos == null) {
            if (g.useBaritone || g.survival) {
                if (portableSupply == null) portableSupply = new SurvivalTasks.ItemGoalTask(g, station.item, 1, false);
                if (!portableSupply.satisfied()) {
                    boolean progress = portableSupply.tick(); movementReason = portableSupply.blockedReason();
                    failureReason = portableSupply.failureReason(); return progress;
                }
                if (portablePlacement == null) portablePlacement = new GrindPlaceStationTask(g, station);
                if (!portablePlacement.satisfied()) {
                    boolean progress = portablePlacement.tick(); movementReason = portablePlacement.blockedReason();
                    failureReason = portablePlacement.failureReason(); return progress;
                }
                pos = g.stationPosition(station);
            } else {
                failureReason = "no placed " + station.item + " was found in loaded chunks nearby"; return false;
            }
        }
        if (!g.withinReach(pos)) {
            if (g.moveNear(pos)) return true;
            movementReason = "Target " + station.item + " at " + GrindExecutor.coordinates(pos)
                    + " is outside reach; enable Baritone or move closer.";
            return true;
        }
        g.cancelMovement();
        g.aimAt(pos);
        if (!g.aimedAt(pos)) return true;
        BlockHitResult hit = g.hitTarget(pos);
        if (hit == null) {
            movementReason = "Target " + station.item + " at " + GrindExecutor.coordinates(pos)
                    + " is blocked from direct interaction.";
            return true;
        }
        int handSlot = safeInteractionSlot();
        if (handSlot < 0) {
            movementReason = "Free a hotbar slot or keep a non-block item there before opening " + station.item + ".";
            return true;
        }
        if (!g.inventory.select(GrindExecutor.OWNER, GrindExecutor.PRIORITY, handSlot, false, true)) return true;
        if (g.client.gameMode == null) return true;
        g.client.gameMode.useItemOn(g.client.player, InteractionHand.MAIN_HAND, hit);
        g.inventory.release(GrindExecutor.OWNER);
        g.rotations.release(GrindExecutor.OWNER);
        interactionIssued = true;
        return true;
    }

    private int safeInteractionSlot() {
        for (int slot = 0; slot < InventoryTransfers.HOTBAR_SIZE; slot++) {
            if (g.inventory.stackAt(slot).isEmpty()) return slot;
        }
        for (int slot = 0; slot < InventoryTransfers.HOTBAR_SIZE; slot++) {
            if (!(g.inventory.stackAt(slot).getItem() instanceof BlockItem)) return slot;
        }
        return -1;
    }

    @Override public String blockedReason() { return movementReason; }
    @Override public String failureReason() { return failureReason; }
    @Override public void cancel() {
        if (portableSupply != null) portableSupply.cancel();
        if (portablePlacement != null) portablePlacement.cancel();
        g.cancelMovement(); g.rotations.release(GrindExecutor.OWNER); g.inventory.release(GrindExecutor.OWNER); g.closeOwnedStationMenu();
    }
}
