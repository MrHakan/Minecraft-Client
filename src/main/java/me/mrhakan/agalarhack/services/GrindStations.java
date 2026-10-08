package me.mrhakan.agalarhack.services;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * The crafting table and furnace a plan uses, and the station menu AutoGrind opened and must close.
 * Moved out of {@link GrindExecutor} in 2.0.05; it reaches the executor's shared state through
 * {@code g}.
 */
final class GrindStations {
    private final GrindExecutor g;
    BlockPos craftingTablePos;
    BlockPos furnacePos;
    int ownedStationMenuId = -1;

    GrindStations(GrindExecutor g) {
        this.g = g;
    }

    void ownMenu() { ownedStationMenuId = g.client.player.containerMenu.containerId; }

    /** A bounded, loaded-chunk-only lookup for existing stations close enough to reuse. */
    BlockPos[] findNearbyStations() {
        BlockPos table = null, furnace = null;
        double tableDistance = Double.MAX_VALUE, furnaceDistance = Double.MAX_VALUE;
        BlockPos origin = g.client.player.blockPosition();
        Vec3 eye = g.client.player.getEyePosition();
        for (int dy = -GrindExecutor.SCAN_VERTICAL; dy <= GrindExecutor.SCAN_VERTICAL; dy++) {
            for (int dx = -GrindExecutor.SCAN_HORIZONTAL; dx <= GrindExecutor.SCAN_HORIZONTAL; dx++) {
                for (int dz = -GrindExecutor.SCAN_HORIZONTAL; dz <= GrindExecutor.SCAN_HORIZONTAL; dz++) {
                    int x = origin.getX() + dx, y = origin.getY() + dy, z = origin.getZ() + dz;
                    LevelChunk chunk = g.scanner.loadedChunk(x >> 4, z >> 4);
                    if (chunk == null || y < g.client.level.getMinY() || y >= g.client.level.getMaxY()) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = chunk.getBlockState(pos).getBlock();
                    double distance = eye.distanceToSqr(Vec3.atCenterOf(pos));
                    if (block == GrindExecutor.Station.TABLE.block && distance < tableDistance) {
                        tableDistance = distance;
                        table = pos.immutable();
                    } else if (block == GrindExecutor.Station.FURNACE.block && distance < furnaceDistance) {
                        furnaceDistance = distance;
                        furnace = pos.immutable();
                    }
                }
            }
        }
        return new BlockPos[]{table, furnace};
    }

    BlockPos stationPosition(GrindExecutor.Station station) {
        return station == GrindExecutor.Station.TABLE ? craftingTablePos : furnacePos;
    }

    void stationPosition(GrindExecutor.Station station, BlockPos pos) {
        if (station == GrindExecutor.Station.TABLE) craftingTablePos = pos == null ? null : pos.immutable();
        else furnacePos = pos == null ? null : pos.immutable();
    }

    boolean stationMenuOpen(GrindExecutor.Station station) {
        if (g.client.player == null) return false;
        return station == GrindExecutor.Station.TABLE
                ? g.client.player.containerMenu instanceof CraftingMenu
                : g.client.player.containerMenu instanceof FurnaceMenu;
    }

    boolean closeOwnedStationMenu() {
        if (ownedStationMenuId < 0 || g.client == null || g.client.player == null) return false;
        if (g.client.player.containerMenu != null && g.client.player.containerMenu.containerId == ownedStationMenuId) {
            // Hiding the screen alone leaves LocalPlayer.containerMenu bound to the station. A
            // later recipe then sees a ghost CraftingMenu while the transfer service correctly
            // refuses clicks because no container screen is visible. Use vanilla's close path so
            // the close packet, local menu reset and screen lifecycle stay in sync.
            g.client.player.closeContainer();
            ownedStationMenuId = -1;
            g.rotations.release(GrindExecutor.OWNER);
            g.inventory.release(GrindExecutor.OWNER);
            return true;
        }
        if (g.client.player.containerMenu == null || g.client.player.containerMenu.containerId != ownedStationMenuId) {
            ownedStationMenuId = -1;
        }
        return false;
    }
}
