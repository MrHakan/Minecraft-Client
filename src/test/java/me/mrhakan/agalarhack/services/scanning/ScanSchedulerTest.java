package me.mrhakan.agalarhack.services.scanning;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static me.mrhakan.agalarhack.services.scanning.ScanScheduler.*;

class ScanSchedulerTest {
    @Test void costsAreAtomicAndNegativeCostsRejected() {
        var budget = new Budget(2, 1, 3);
        assertFalse(budget.take(1, 2, 1));
        assertTrue(budget.take(2, 1, 3));
        assertFalse(budget.take(1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> budget.take(-1, 0, 0));
    }
    @Test void peersShareBudgetAndTinyBudgetRotatesAcrossTicks() {
        var scheduler = new ScanScheduler<String>((owner, error) -> fail(error));
        var visited = new ArrayList<String>();
        for (int tick = 0; tick < 2; tick++) {
            for (String owner : new String[]{"a", "b"}) scheduler.offer(owner, Priority.BACKGROUND, 100,
                    budget -> { if (!budget.take(1, 0, 0)) return Result.BLOCKED; visited.add(owner); return Result.MORE; });
            scheduler.run(1, 1, 1);
            assertEquals(1, scheduler.lastUsage().blocks());
        }
        assertEquals(2, visited.stream().distinct().count());
    }
    @Test void failureCancellationAndExpiredWorkDoNotSuppressPeers() {
        var failures = new ArrayList<String>();
        var scheduler = new ScanScheduler<String>((owner, error) -> failures.add(owner));
        scheduler.offer("broken", Priority.NEAR, 10, budget -> { throw new IllegalStateException(); });
        scheduler.offer("cancelled", Priority.NEAR, 10, budget -> { fail("Cancelled task ran"); return Result.DONE; });
        scheduler.cancel("cancelled");
        scheduler.offer("working", Priority.BACKGROUND, 10, budget -> budget.take(0, 1, 0) ? Result.MORE : Result.BLOCKED);
        scheduler.run(0, 2, 0);
        assertEquals(2, scheduler.lastUsage().chunkLookups());
        assertEquals(java.util.List.of("broken"), failures);
        scheduler.run(100, 100, 100);
        assertEquals(0, scheduler.lastUsage().steps());
    }
    @Test void nonConsumingTaskCannotSpinForever() {
        var scheduler = new ScanScheduler<String>((owner, error) -> fail(error));
        scheduler.offer("bad", Priority.NEAR, 16384, budget -> Result.MORE);
        scheduler.run(1, 1, 1);
        assertEquals(16384, scheduler.lastUsage().steps());
    }
}
