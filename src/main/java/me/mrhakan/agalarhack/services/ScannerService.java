package me.mrhakan.agalarhack.services;

import java.util.HashMap;
import java.util.Map;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.scanning.ScanScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Shared tick budgets and a short-lived loaded-chunk lookup cache; never loads missing chunks. */
public final class ScannerService {
    public static final int BLOCK_BUDGET = 12000, CHUNK_BUDGET = 64, ENTITY_BUDGET = 4096;
    private final Minecraft mc;
    private final Map<Long, LevelChunk> chunks = new HashMap<>();
    private final ScanScheduler<Module> scheduler = new ScanScheduler<>((module, error) -> {
        AgalarHackClient.LOGGER.error("Scanner failed for {}", module.getName(), error);
        module.setToggled(false);
        ClientServices.require(NotificationService.class).publish(NotificationService.Type.ERROR,
                module.getName() + " disabled after a scanner error");
    });
    public ScannerService(Minecraft mc) { this.mc = mc; }
    public void offer(Module owner, ScanScheduler.Priority priority, int steps, ScanScheduler.Task task) {
        if (owner.isToggled()) scheduler.offer(owner, priority, steps, task);
    }
    public void cancel(Module owner) { scheduler.cancel(owner); }
    public void reset() { scheduler.clear(); chunks.clear(); }
    public void tick() {
        chunks.clear();
        if (mc.level == null || mc.player == null || !mc.player.isAlive()) { reset(); return; }
        try { scheduler.run(BLOCK_BUDGET, CHUNK_BUDGET, ENTITY_BUDGET); }
        finally { chunks.clear(); }
    }
    public ScanScheduler.Usage lastUsage() { return scheduler.lastUsage(); }
    private static long key(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
    /** Reserve a lookup before calling loadedChunk; false means the cursor must pause unchanged. */
    public boolean reserveChunk(ScanScheduler.Budget budget, int x, int z) {
        return chunks.containsKey(key(x, z)) || budget.take(0, 1, 0);
    }
    public LevelChunk loadedChunk(int x, int z) {
        long key = key(x, z);
        if (!chunks.containsKey(key)) {
            if (chunks.size() >= CHUNK_BUDGET) throw new IllegalStateException("Chunk lookup without budget");
            chunks.put(key, mc.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false));
        }
        return chunks.get(key);
    }
}
