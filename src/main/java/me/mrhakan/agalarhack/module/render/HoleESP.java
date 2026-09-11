package me.mrhakan.agalarhack.module.render;

import java.util.LinkedHashMap;
import java.util.Map;
import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.events.EventBus;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.ScannerService;
import me.mrhakan.agalarhack.services.scanning.BlockScanCursor;
import me.mrhakan.agalarhack.services.scanning.HoleDetector;
import me.mrhakan.agalarhack.services.scanning.ScanScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Marks standable holes nearby, distinguishing blast-resistant ones from the rest. */
public class HoleESP extends Module {
    /** Vanilla obsidian is 1200; anything at or above this survives a crystal. */
    private static final float RESISTANT_THRESHOLD = 600f;

    private final Map<BlockPos, HoleDetector.Hole> holes = new LinkedHashMap<>();
    private final BlockScanCursor cursor = new BlockScanCursor();
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    private java.util.List<Map.Entry<BlockPos, HoleDetector.Hole>> snapshot = java.util.List.of();
    private boolean snapshotDirty;
    private boolean anchorSet;
    private int anchorX, anchorY, anchorZ;
    private long scanCycle;
    private String signature = "";
    private EventBus.Subscription chunkUnload;
    private EventBus.Subscription blockUpdate;

    public HoleESP() {
        super("HoleESP", Category.RENDER, "Marks standable holes nearby and whether they are blast resistant");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("horizontalRange", 16.0, 4.0, 32.0, "Horizontal search radius in blocks");
        addNumberSetting("verticalRange", 6.0, 2.0, 16.0, "Vertical search radius in blocks");
        addNumberSetting("scanBudget", 2000.0, 100.0, 12000.0, "Block positions requested per tick; shared scanner limits also apply");
        addNumberSetting("maxResults", 128.0, 8.0, 512.0, "Maximum marked holes");
        addBooleanSetting("safeOnly", false, "Only mark fully blast-resistant holes");
        addNumberSetting("alpha", 150.0, 32.0, 255.0, "Marker alpha channel");
    }

    public java.util.List<Map.Entry<BlockPos, HoleDetector.Hole>> results() {
        if (snapshotDirty) { snapshot = java.util.List.copyOf(holes.entrySet().stream().toList()); snapshotDirty = false; }
        return snapshot;
    }

    @Override
    public void onEnable() {
        reset();
        closeSubscriptions();
        EventBus events = service(EventBus.class);
        chunkUnload = events.subscribe(ClientEvents.ChunkUnloaded.class, "hole-esp-unload", 0, event -> {
            if (event.level() != mc.level) return;
            var chunk = event.chunk().getPos();
            if (holes.keySet().removeIf(pos -> (pos.getX() >> 4) == chunk.x() && (pos.getZ() >> 4) == chunk.z())) {
                snapshotDirty = true;
            }
        });
        // A single block change can create or destroy a hole, and it is cheaper to restart the
        // sweep for that area than to re-derive which candidates the change touched.
        blockUpdate = events.subscribe(ClientEvents.BlockUpdated.class, "hole-esp-update", 0, event -> {
            if (event.level() != mc.level || !anchorSet) return;
            BlockPos pos = event.pos();
            if (Math.abs(pos.getX() - anchorX) > 2 + getNumberSetting("horizontalRange", 16)
                    || Math.abs(pos.getZ() - anchorZ) > 2 + getNumberSetting("horizontalRange", 16)) return;
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
        holes.clear();
        snapshot = java.util.List.of();
        snapshotDirty = false;
        anchorSet = false;
        signature = "";
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null) return;
        int horizontal = (int) Math.round(getNumberSetting("horizontalRange", 16));
        int vertical = (int) Math.round(getNumberSetting("verticalRange", 6));
        String next = horizontal + ":" + vertical + ":" + getBooleanSetting("safeOnly", false);
        if (!anchorSet || !next.equals(signature)
                || Math.abs(mc.player.getBlockX() - anchorX) > 2
                || Math.abs(mc.player.getBlockY() - anchorY) > 2
                || Math.abs(mc.player.getBlockZ() - anchorZ) > 2) {
            anchorX = mc.player.getBlockX();
            anchorY = mc.player.getBlockY();
            anchorZ = mc.player.getBlockZ();
            signature = next;
            anchorSet = true;
            cursor.reset(anchorX, anchorY, anchorZ, horizontal, vertical);
            holes.clear();
            snapshotDirty = true;
        }
        int budget = Math.max(100, Math.min(12000, (int) Math.round(getNumberSetting("scanBudget", 2000))));
        scanCycle = cursor.cycle();
        service(ScannerService.class).offer(this, ScanScheduler.Priority.BACKGROUND, budget, this::scanStep);
    }

    private ScanScheduler.Result scanStep(ScanScheduler.Budget budget) {
        if (cursor.cycle() != scanCycle) return ScanScheduler.Result.DONE;
        int x = cursor.x(), y = cursor.y(), z = cursor.z();
        ScannerService scanner = service(ScannerService.class);
        // Five probes per candidate, so reserve accordingly rather than under-reporting the cost.
        if (!scanner.reserveBlock(budget, x >> 4, z >> 4) || !budget.take(5, 0, 0)) return ScanScheduler.Result.BLOCKED;
        if (!mc.level.isInsideBuildHeight(y) || !mc.level.isInsideBuildHeight(y + 1)
                || !mc.level.isInsideBuildHeight(y - 1)) {
            cursor.advance();
            return ScanScheduler.Result.MORE;
        }
        var hole = classifyAt(x, y, z);
        BlockPos key = new BlockPos(x, y, z);
        boolean wanted = hole != HoleDetector.Hole.NONE
                && (!getBooleanSetting("safeOnly", false) || hole == HoleDetector.Hole.SAFE);
        if (wanted) {
            if (holes.size() < (int) Math.round(getNumberSetting("maxResults", 128))
                    && holes.put(key, hole) != hole) snapshotDirty = true;
        } else if (holes.remove(key) != null) {
            snapshotDirty = true;
        }
        cursor.advance();
        return ScanScheduler.Result.MORE;
    }

    private HoleDetector.Hole classifyAt(int x, int y, int z) {
        boolean feetClear = open(x, y, z);
        boolean headClear = open(x, y + 1, z);
        HoleDetector.Material[] sides = {
                material(x + 1, y, z), material(x - 1, y, z),
                material(x, y, z + 1), material(x, y, z - 1)
        };
        return HoleDetector.classify(material(x, y - 1, z), sides, feetClear, headClear);
    }

    private boolean open(int x, int y, int z) {
        return material(x, y, z) == HoleDetector.Material.OPEN;
    }

    private HoleDetector.Material material(int x, int y, int z) {
        probe.set(x, y, z);
        BlockState state = mc.level.getBlockState(probe);
        if (state.isAir() || !state.blocksMotion()) return HoleDetector.Material.OPEN;
        return state.getBlock().getExplosionResistance() >= RESISTANT_THRESHOLD
                ? HoleDetector.Material.RESISTANT : HoleDetector.Material.WEAK;
    }
}
