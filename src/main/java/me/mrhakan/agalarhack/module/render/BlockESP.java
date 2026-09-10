package me.mrhakan.agalarhack.module.render;

import java.util.Iterator;
import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.events.EventBus;
import me.mrhakan.agalarhack.services.ScannerService;
import me.mrhakan.agalarhack.services.scanning.BlockScanCursor;
import me.mrhakan.agalarhack.services.scanning.ScanScheduler;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/** Incremental, bounded block scanner for valuable/interesting blocks already loaded client-side. */
public class BlockESP extends Module {
    private final Set<BlockPos> matches = new LinkedHashSet<>();
    private int anchorX;
    private int anchorY;
    private int anchorZ;
    private final BlockScanCursor cursor = new BlockScanCursor();
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    private List<BlockPos> snapshot = List.of();
    private boolean snapshotDirty;
    private EventBus.Subscription chunkUnload;
    private boolean anchorSet;
    private String targetSignature = "";

    public BlockESP() {
        super("BlockESP", Category.RENDER, "Incrementally highlights selected loaded block categories without frame-time brute force");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("horizontalRange", 24.0, 4.0, 64.0, "Horizontal scan radius in blocks");
        addNumberSetting("verticalRange", 16.0, 4.0, 48.0, "Vertical scan radius in blocks");
        addNumberSetting("scanBudget", 2500.0, 100.0, 12000.0, "Maximum block positions requested per tick; shared scanner limits also apply");
        addNumberSetting("maxResults", 1024.0, 16.0, 4096.0, "Maximum cached highlighted blocks");
        addBooleanSetting("valuableOres", true, "Highlight diamond, emerald and ancient debris");
        addBooleanSetting("commonOres", false, "Highlight all other vanilla ore blocks");
        addBooleanSetting("spawners", true, "Highlight normal and trial spawners");
        addBooleanSetting("portals", false, "Highlight loaded portal and gateway blocks");
        addBooleanSetting("beacons", false, "Highlight beacon blocks");
        addBooleanSetting("distanceFade", true, "Fade highlighted blocks toward the horizontal range limit");
        addNumberSetting("red", 255.0, 0.0, 255.0, "Block overlay red channel");
        addNumberSetting("green", 100.0, 0.0, 255.0, "Block overlay green channel");
        addNumberSetting("blue", 220.0, 0.0, 255.0, "Block overlay blue channel");
        addNumberSetting("alpha", 220.0, 32.0, 255.0, "Block overlay alpha channel");
    }

    @Override
    public void onEnable() {
        resetScanner();
        if (chunkUnload != null) chunkUnload.close();
        chunkUnload = service(EventBus.class).subscribe(ClientEvents.ChunkUnloaded.class, "block-esp-cache", 0, event -> {
            if (event.level() != mc.level) return;
            var chunk = event.chunk().getPos();
            if (matches.removeIf(pos -> (pos.getX() >> 4) == chunk.x && (pos.getZ() >> 4) == chunk.z)) snapshotDirty = true;
        });
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null) {
            return;
        }

        int horizontal = (int) Math.round(getNumberSetting("horizontalRange", 24.0));
        int vertical = (int) Math.round(getNumberSetting("verticalRange", 16.0));
        String signature = signature();
        if (!anchorSet
                || Math.abs(mc.player.getBlockX() - anchorX) > 4
                || Math.abs(mc.player.getBlockY() - anchorY) > 4
                || Math.abs(mc.player.getBlockZ() - anchorZ) > 4
                || !signature.equals(targetSignature)) {
            anchorX = mc.player.getBlockX();
            anchorY = mc.player.getBlockY();
            anchorZ = mc.player.getBlockZ();
            targetSignature = signature;
            anchorSet = true;
            cursor.reset(anchorX, anchorY, anchorZ, horizontal, vertical);
            matches.clear();
            snapshotDirty = true;
        }

        trimResults();
        int budget = Math.max(100, Math.min(12000, (int) Math.round(getNumberSetting("scanBudget", 2500.0))));
        service(ScannerService.class).offer(this, ScanScheduler.Priority.BACKGROUND, budget, this::scanStep);
    }

    private ScanScheduler.Result scanStep(ScanScheduler.Budget budget) {
        int x = cursor.x(), y = cursor.y(), z = cursor.z();
        if (!mc.level.isInsideBuildHeight(y)) {
            if (!budget.take(1, 0, 0)) return ScanScheduler.Result.BLOCKED;
            cursor.advance();
            return ScanScheduler.Result.MORE;
        }
        ScannerService scanner = service(ScannerService.class);
        if (!scanner.reserveBlock(budget, x >> 4, z >> 4)) return ScanScheduler.Result.BLOCKED;
        var chunk = scanner.loadedChunk(x >> 4, z >> 4);
        if (chunk == null) {
            cursor.skipChunk();
            return ScanScheduler.Result.MORE;
        }
        probe.set(x, y, z);
        BlockState state = chunk.getBlockState(probe);
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (matches(id)) {
            if (!matches.contains(probe)) {
                matches.add(new BlockPos(x, y, z));
                snapshotDirty = true;
                trimResults();
            }
        } else if (matches.remove(probe)) snapshotDirty = true;
        cursor.advance();
        return ScanScheduler.Result.MORE;
    }

    @Override
    public void onDisable() {
        service(ScannerService.class).cancel(this);
        if (chunkUnload != null) { chunkUnload.close(); chunkUnload = null; }
        resetScanner();
    }

    public List<BlockPos> getMatches() {
        if (snapshotDirty) { snapshot = List.copyOf(matches); snapshotDirty = false; }
        return snapshot;
    }

    public boolean matches(String id) {
        if (id == null) {
            return false;
        }
        if (getBooleanSetting("valuableOres", true)
                && (id.endsWith(":diamond_ore") || id.endsWith(":deepslate_diamond_ore")
                        || id.endsWith(":emerald_ore") || id.endsWith(":deepslate_emerald_ore")
                        || id.endsWith(":ancient_debris"))) {
            return true;
        }
        if (getBooleanSetting("commonOres", false)
                && (id.endsWith("_ore") || id.endsWith(":nether_quartz_ore") || id.endsWith(":nether_gold_ore"))) {
            return true;
        }
        if (getBooleanSetting("spawners", true)
                && (id.endsWith(":spawner") || id.endsWith(":trial_spawner"))) {
            return true;
        }
        if (getBooleanSetting("portals", false)
                && (id.endsWith(":nether_portal") || id.endsWith(":end_portal") || id.endsWith(":end_gateway"))) {
            return true;
        }
        return getBooleanSetting("beacons", false) && id.endsWith(":beacon");
    }

    private String signature() {
        return getBooleanSetting("valuableOres", true) + ":"
                + getBooleanSetting("commonOres", false) + ":"
                + getBooleanSetting("spawners", true) + ":"
                + getBooleanSetting("portals", false) + ":"
                + getBooleanSetting("beacons", false) + ":"
                + (int) Math.round(getNumberSetting("horizontalRange", 24.0)) + ":"
                + (int) Math.round(getNumberSetting("verticalRange", 16.0));
    }

    private void trimResults() {
        int max = (int) Math.round(getNumberSetting("maxResults", 1024.0));
        while (matches.size() > max) {
            Iterator<BlockPos> iterator = matches.iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
            snapshotDirty = true;
        }
    }

    private void resetScanner() {
        matches.clear();
        anchorSet = false;
        snapshot = List.of();
        snapshotDirty = false;
        targetSignature = "";
    }
}
