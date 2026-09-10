package me.mrhakan.agalarhack.module.render;

import java.util.ArrayList;
import java.util.List;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Periodically caches loaded storage block entities for the world overlay renderer. */
public class StorageESP extends Module {
    private final List<BlockPos> cachedPositions = new ArrayList<>();
    private int ticksUntilScan;

    public StorageESP() {
        super("StorageESP", Category.RENDER, "Highlights loaded storage and utility block entities with bounded scans");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("range", 64.0, 16.0, 160.0, "Maximum storage scan/render distance in blocks");
        addNumberSetting("scanInterval", 10.0, 1.0, 40.0, "Ticks between loaded block-entity scans");
        addNumberSetting("maxResults", 512.0, 16.0, 2048.0, "Maximum cached storage markers");
        addBooleanSetting("chests", true, "Highlight normal and trapped chests");
        addBooleanSetting("enderChests", true, "Highlight ender chests");
        addBooleanSetting("barrels", true, "Highlight barrels");
        addBooleanSetting("shulkers", true, "Highlight shulker boxes");
        addBooleanSetting("utilities", true, "Highlight hoppers, furnaces, dispensers, droppers, brewing stands and crafters");
        addBooleanSetting("labels", false, "Show a short storage type label above highlighted blocks");
        addBooleanSetting("distanceFade", true, "Fade storage markers toward the configured range limit");
        addNumberSetting("alpha", 220.0, 32.0, 255.0, "Storage overlay alpha channel");
    }

    @Override
    public void onEnable() {
        ticksUntilScan = 0;
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (ticksUntilScan-- > 0) {
            return;
        }
        ticksUntilScan = Math.max(1, (int) Math.round(getNumberSetting("scanInterval", 10.0))) - 1;
        scanLoadedStorage();
    }

    @Override
    public void onDisable() {
        cachedPositions.clear();
        ticksUntilScan = 0;
    }

    public List<BlockPos> getCachedPositions() {
        return List.copyOf(cachedPositions);
    }

    public boolean matches(String blockId) {
        if (blockId == null) {
            return false;
        }
        if (blockId.endsWith(":ender_chest")) {
            return getBooleanSetting("enderChests", true);
        }
        if (blockId.endsWith(":chest") || blockId.endsWith(":trapped_chest")) {
            return getBooleanSetting("chests", true);
        }
        if (blockId.endsWith(":barrel")) {
            return getBooleanSetting("barrels", true);
        }
        if (blockId.endsWith("_shulker_box")) {
            return getBooleanSetting("shulkers", true);
        }
        return getBooleanSetting("utilities", true) && isUtilityStorage(blockId);
    }

    public static boolean isUtilityStorage(String blockId) {
        return blockId.endsWith(":hopper")
                || blockId.endsWith(":furnace")
                || blockId.endsWith(":smoker")
                || blockId.endsWith(":blast_furnace")
                || blockId.endsWith(":dispenser")
                || blockId.endsWith(":dropper")
                || blockId.endsWith(":brewing_stand")
                || blockId.endsWith(":crafter");
    }

    private void scanLoadedStorage() {
        cachedPositions.clear();
        int range = (int) Math.round(getNumberSetting("range", 64.0));
        int maxResults = (int) Math.round(getNumberSetting("maxResults", 512.0));
        int minimumChunkX = Math.floorDiv(mc.player.getBlockX() - range, 16);
        int maximumChunkX = Math.floorDiv(mc.player.getBlockX() + range, 16);
        int minimumChunkZ = Math.floorDiv(mc.player.getBlockZ() - range, 16);
        int maximumChunkZ = Math.floorDiv(mc.player.getBlockZ() + range, 16);
        double rangeSq = range * (double) range;

        outer:
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity == null || blockEntity.isRemoved()) {
                        continue;
                    }
                    BlockPos pos = blockEntity.getBlockPos();
                    double dx = pos.getX() + 0.5 - mc.player.getX();
                    double dy = pos.getY() + 0.5 - mc.player.getY();
                    double dz = pos.getZ() + 0.5 - mc.player.getZ();
                    if (dx * dx + dy * dy + dz * dz > rangeSq) {
                        continue;
                    }
                    String id = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()).toString();
                    if (matches(id)) {
                        cachedPositions.add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
                        if (cachedPositions.size() >= maxResults) {
                            break outer;
                        }
                    }
                }
            }
        }
    }
}
