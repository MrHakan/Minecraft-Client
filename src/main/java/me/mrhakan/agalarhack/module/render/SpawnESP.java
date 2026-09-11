package me.mrhakan.agalarhack.module.render;

import java.util.LinkedHashMap;
import java.util.Map;
import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.events.EventBus;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.ScannerService;
import me.mrhakan.agalarhack.services.scanning.BlockScanCursor;
import me.mrhakan.agalarhack.services.scanning.ScanScheduler;
import me.mrhakan.agalarhack.services.scanning.SpawnLightRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;

/**
 * Marks positions where light alone permits hostile mob spawning.
 *
 * <p>The description says "light permits" rather than "mobs will spawn" on purpose: biome rules,
 * mob-specific placement, spawn caps and difficulty are all outside what the client checks here.
 */
public class SpawnESP extends Module {
    private final Map<BlockPos, SpawnLightRules.Spawnable> spots = new LinkedHashMap<>();
    private final BlockScanCursor cursor = new BlockScanCursor();
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    private java.util.List<Map.Entry<BlockPos, SpawnLightRules.Spawnable>> snapshot = java.util.List.of();
    private boolean snapshotDirty;
    private boolean anchorSet;
    private int anchorX, anchorY, anchorZ;
    private long scanCycle;
    private String signature = "";
    private EventBus.Subscription chunkUnload;
    private EventBus.Subscription blockUpdate;

    public SpawnESP() {
        super("SpawnESP", Category.RENDER, "Marks spots where light level alone permits hostile spawning; not a spawn prediction");
        markExperimental();
    }

    @Override
    public void selfSettings() {
        addNumberSetting("horizontalRange", 24.0, 4.0, 48.0, "Horizontal search radius in blocks");
        addNumberSetting("verticalRange", 8.0, 2.0, 24.0, "Vertical search radius in blocks");
        addNumberSetting("scanBudget", 2500.0, 100.0, 12000.0, "Block positions requested per tick; shared scanner limits also apply");
        addNumberSetting("maxResults", 512.0, 16.0, 2048.0, "Maximum marked positions");
        addBooleanSetting("alwaysOnly", false, "Only mark spots spawnable at any time of day");
        addNumberSetting("alpha", 120.0, 32.0, 255.0, "Marker alpha channel");
    }

    public java.util.List<Map.Entry<BlockPos, SpawnLightRules.Spawnable>> results() {
        if (snapshotDirty) { snapshot = java.util.List.copyOf(spots.entrySet().stream().toList()); snapshotDirty = false; }
        return snapshot;
    }

    @Override
    public void onEnable() {
        reset();
        closeSubscriptions();
        EventBus events = service(EventBus.class);
        chunkUnload = events.subscribe(ClientEvents.ChunkUnloaded.class, "spawn-esp-unload", 0, event -> {
            if (event.level() != mc.level) return;
            var chunk = event.chunk().getPos();
            if (spots.keySet().removeIf(pos -> (pos.getX() >> 4) == chunk.x() && (pos.getZ() >> 4) == chunk.z())) {
                snapshotDirty = true;
            }
        });
        // Placing or breaking a light source changes a whole neighbourhood, so the sweep restarts
        // rather than trying to work out which positions the change reached.
        blockUpdate = events.subscribe(ClientEvents.BlockUpdated.class, "spawn-esp-update", 0, event -> {
            if (event.level() != mc.level || !anchorSet) return;
            double range = getNumberSetting("horizontalRange", 24) + 16;
            if (Math.abs(event.pos().getX() - anchorX) > range || Math.abs(event.pos().getZ() - anchorZ) > range) return;
            anchorSet = false;
        });
    }

    @Override
    public void onDisable() {
        service(ScannerService.class).cancel(this);
        closeSubscriptions();
        reset();
    }

    private void closeSubscriptions() {
        if (chunkUnload != null) { chunkUnload.close(); chunkUnload = null; }
        if (blockUpdate != null) { blockUpdate.close(); blockUpdate = null; }
    }

    private void reset() {
        spots.clear();
        snapshot = java.util.List.of();
        snapshotDirty = false;
        anchorSet = false;
        signature = "";
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null) return;
        int horizontal = (int) Math.round(getNumberSetting("horizontalRange", 24));
        int vertical = (int) Math.round(getNumberSetting("verticalRange", 8));
        String next = horizontal + ":" + vertical + ":" + getBooleanSetting("alwaysOnly", false);
        if (!anchorSet || !next.equals(signature)
                || Math.abs(mc.player.getBlockX() - anchorX) > 4
                || Math.abs(mc.player.getBlockY() - anchorY) > 4
                || Math.abs(mc.player.getBlockZ() - anchorZ) > 4) {
            anchorX = mc.player.getBlockX();
            anchorY = mc.player.getBlockY();
            anchorZ = mc.player.getBlockZ();
            signature = next;
            anchorSet = true;
            cursor.reset(anchorX, anchorY, anchorZ, horizontal, vertical);
            spots.clear();
            snapshotDirty = true;
        }
        int budget = Math.max(100, Math.min(12000, (int) Math.round(getNumberSetting("scanBudget", 2500))));
        scanCycle = cursor.cycle();
        service(ScannerService.class).offer(this, ScanScheduler.Priority.BACKGROUND, budget, this::scanStep);
    }

    private ScanScheduler.Result scanStep(ScanScheduler.Budget budget) {
        if (cursor.cycle() != scanCycle) return ScanScheduler.Result.DONE;
        int x = cursor.x(), y = cursor.y(), z = cursor.z();
        ScannerService scanner = service(ScannerService.class);
        // Three block probes plus two light lookups per candidate.
        if (!scanner.reserveBlock(budget, x >> 4, z >> 4) || !budget.take(3, 0, 0)) return ScanScheduler.Result.BLOCKED;
        if (!mc.level.isInsideBuildHeight(y) || !mc.level.isInsideBuildHeight(y + 1)
                || !mc.level.isInsideBuildHeight(y - 1)) {
            cursor.advance();
            return ScanScheduler.Result.MORE;
        }
        var spawnable = classifyAt(x, y, z);
        BlockPos key = new BlockPos(x, y, z);
        boolean wanted = spawnable != SpawnLightRules.Spawnable.NONE
                && (!getBooleanSetting("alwaysOnly", false) || spawnable == SpawnLightRules.Spawnable.ALWAYS);
        if (wanted) {
            if (spots.size() < (int) Math.round(getNumberSetting("maxResults", 512))
                    && spots.put(key, spawnable) != spawnable) snapshotDirty = true;
        } else if (spots.remove(key) != null) {
            snapshotDirty = true;
        }
        cursor.advance();
        return ScanScheduler.Result.MORE;
    }

    private SpawnLightRules.Spawnable classifyAt(int x, int y, int z) {
        probe.set(x, y - 1, z);
        boolean floorSolid = mc.level.getBlockState(probe).blocksMotion();
        probe.set(x, y, z);
        boolean feetClear = !mc.level.getBlockState(probe).blocksMotion();
        var engine = mc.level.getLightEngine();
        int blockLight = engine.getLayerListener(LightLayer.BLOCK).getLightValue(probe);
        int skyLight = engine.getLayerListener(LightLayer.SKY).getLightValue(probe);
        probe.set(x, y + 1, z);
        boolean headClear = !mc.level.getBlockState(probe).blocksMotion();
        return SpawnLightRules.classify(blockLight, skyLight, floorSolid && feetClear && headClear);
    }
}
