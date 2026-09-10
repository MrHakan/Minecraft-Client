package me.mrhakan.agalarhack.module.render;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.events.EventBus;
import me.mrhakan.agalarhack.services.ScannerService;
import me.mrhakan.agalarhack.services.scanning.ChunkScanOrder;
import me.mrhakan.agalarhack.services.scanning.ScanScheduler;
import me.mrhakan.agalarhack.services.scanning.StorageKind;
import java.util.List;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/** Incremental loaded-chunk storage scans, scheduled alongside other scanners with shared budgets. */
public class StorageESP extends Module {
    private List<BlockPos> cachedPositions = List.of();
    private final Set<BlockPos> passMatches = new LinkedHashSet<>();
    private List<ChunkScanOrder.Chunk> scanChunks = List.of();
    private int ticksUntilScan, chunkIndex, visitedInChunk, anchorX, anchorZ;
    private int range, maximumResults;
    private String signature = "";
    private LevelChunk activeChunk;
    private Iterator<BlockEntity> iterator;
    private boolean scanning;
    private EventBus.Subscription chunkUnload;

    public StorageESP() {
        super("StorageESP", Category.RENDER, "Highlights loaded storage and utility block entities with bounded scans");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("range", 64.0, 16.0, 160.0, "Maximum storage scan/render distance in blocks");
        addNumberSetting("scanInterval", 10.0, 1.0, 40.0, "Ticks between completed incremental scan passes");
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
        reset();
        if (chunkUnload != null) chunkUnload.close();
        chunkUnload = service(EventBus.class).subscribe(ClientEvents.ChunkUnloaded.class, "storage-esp-cache", 0, event -> {
            if (event.level() != mc.level) return;
            var chunk = event.chunk().getPos();
            passMatches.removeIf(pos -> inChunk(pos, chunk.x, chunk.z));
            cachedPositions = cachedPositions.stream().filter(pos -> !inChunk(pos, chunk.x, chunk.z)).toList();
            if (activeChunk == event.chunk()) finishChunk();
        });
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null) return;
        int nextRange = (int)Math.round(getNumberSetting("range", 64.0));
        int nextMaximum = (int)Math.round(getNumberSetting("maxResults", 512.0));
        String nextSignature = nextRange + ":" + nextMaximum + ":" + getBooleanSetting("chests", true)
                + ":" + getBooleanSetting("enderChests", true) + ":" + getBooleanSetting("barrels", true)
                + ":" + getBooleanSetting("shulkers", true) + ":" + getBooleanSetting("utilities", true);
        if (!signature.equals(nextSignature) || Math.abs(mc.player.getBlockX() - anchorX) > 16
                || Math.abs(mc.player.getBlockZ() - anchorZ) > 16) {
            reset();
            signature = nextSignature;
        }
        if (!scanning) {
            if (ticksUntilScan-- > 0) return;
            range = nextRange;
            maximumResults = nextMaximum;
            anchorX = mc.player.getBlockX(); anchorZ = mc.player.getBlockZ();
            scanChunks = ChunkScanOrder.around(anchorX, anchorZ, range);
            chunkIndex = 0; visitedInChunk = 0;
            passMatches.clear();
            scanning = true;
        }
        service(ScannerService.class).offer(this, ScanScheduler.Priority.BACKGROUND, 8192, this::scanStep);
    }

    private ScanScheduler.Result scanStep(ScanScheduler.Budget budget) {
        if (chunkIndex >= scanChunks.size() || passMatches.size() >= maximumResults) {
            cachedPositions = List.copyOf(passMatches);
            passMatches.clear(); scanChunks = List.of();
            iterator = null; activeChunk = null; scanning = false;
            ticksUntilScan = Math.max(1, (int)Math.round(getNumberSetting("scanInterval", 10.0))) - 1;
            return ScanScheduler.Result.DONE;
        }
        var position = scanChunks.get(chunkIndex);
        ScannerService scanner = service(ScannerService.class);
        if (!scanner.reserveChunk(budget, position.x(), position.z())) return ScanScheduler.Result.BLOCKED;
        LevelChunk chunk = scanner.loadedChunk(position.x(), position.z());
        if (chunk == null || visitedInChunk >= 4096) {
            finishChunk();
            return ScanScheduler.Result.MORE;
        }
        if (chunk != activeChunk || iterator == null) {
            // A chunk replacement or map mutation invalidates the borrowed iterator and partial results.
            if (activeChunk != null || visitedInChunk > 0) passMatches.removeIf(pos -> inChunk(pos, position.x(), position.z()));
            activeChunk = chunk;
            iterator = chunk.getBlockEntities().values().iterator();
        }
        try {
            if (!iterator.hasNext()) { finishChunk(); return ScanScheduler.Result.MORE; }
            if (!budget.take(0, 0, 1)) return ScanScheduler.Result.BLOCKED;
            visitedInChunk++;
            BlockEntity entity = iterator.next();
            if (entity == null || entity.isRemoved()) return ScanScheduler.Result.MORE;
            BlockPos pos = entity.getBlockPos();
            double dx = pos.getX() + 0.5 - mc.player.getX(), dy = pos.getY() + 0.5 - mc.player.getY();
            double dz = pos.getZ() + 0.5 - mc.player.getZ();
            if (dx * dx + dy * dy + dz * dz <= range * (double)range
                    && matches(BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock()).toString())) {
                passMatches.add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
            }
        } catch (ConcurrentModificationException changed) {
            iterator = null;
            // The visit cap is retained across restarts, so a constantly changing chunk cannot monopolize scanning.
        }
        return ScanScheduler.Result.MORE;
    }

    private static boolean inChunk(BlockPos pos, int x, int z) {
        return (pos.getX() >> 4) == x && (pos.getZ() >> 4) == z;
    }
    private void finishChunk() { chunkIndex++; visitedInChunk = 0; iterator = null; activeChunk = null; }
    private void reset() {
        cachedPositions = List.of(); passMatches.clear(); scanChunks = List.of();
        iterator = null; activeChunk = null; scanning = false;
        ticksUntilScan = 0; chunkIndex = 0; visitedInChunk = 0; signature = "";
    }
    @Override
    public void onDisable() {
        service(ScannerService.class).cancel(this);
        if (chunkUnload != null) { chunkUnload.close(); chunkUnload = null; }
        reset();
    }
    public List<BlockPos> getCachedPositions() { return cachedPositions; }
    public boolean matches(String id) {
        return switch (StorageKind.of(id)) {
            case CHEST -> getBooleanSetting("chests", true);
            case ENDER_CHEST -> getBooleanSetting("enderChests", true);
            case BARREL -> getBooleanSetting("barrels", true);
            case SHULKER -> getBooleanSetting("shulkers", true);
            case UTILITY -> getBooleanSetting("utilities", true);
            case OTHER -> false;
        };
    }
    public static boolean isUtilityStorage(String id) { return StorageKind.of(id) == StorageKind.UTILITY; }
}
