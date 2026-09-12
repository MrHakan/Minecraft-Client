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
        assertEquals(ScanScheduler.MAX_IDLE_STEPS, scheduler.lastUsage().steps(),
                "a task that spends nothing is stopped by the idle allowance");
    }

    /**
     * The step ceiling must not sit below what the configured budget allows.
     *
     * <p>It was a flat 16384, while the "high" profile grants 28,000 blocks plus 128 chunk lookups
     * and 8,192 entities. A scanner that spends one unit per step - BlockESP and the entity walk
     * both do - therefore stopped at 16,384 units however high the budget was set, so choosing
     * "high" bought a player nothing over "balanced". Two ceilings, and the one nobody could see
     * won.
     *
     * <p>Several owners, because the ceiling is shared across all of them rather than per task.
     */
    @Test void theStepCeilingDoesNotCutTheConfiguredBudgetShort() {
        var scheduler = new ScanScheduler<String>((owner, error) -> fail(error));
        var budgets = ScanBudgets.forProfile("high");
        int[] spent = { 0 };
        for (int owner = 0; owner < 4; owner++) {
            scheduler.offer("owner" + owner, Priority.NEAR, 16384, budget -> {
                if (!budget.take(1, 0, 0)) return Result.BLOCKED;
                spent[0]++;
                return Result.MORE;
            });
        }
        scheduler.run(budgets.blocks(), budgets.chunkLookups(), budgets.entities());
        assertTrue(spent[0] > 16384,
                "the high profile grants " + budgets.blocks() + " blocks but only " + spent[0]
                        + " were spendable, so the step ceiling is the real limit");
    }

    /**
     * The runaway guard does not loosen just because the budget is large.
     *
     * <p>Tying the ceiling to the budget is only safe if the thing it actually guards against - a
     * task returning MORE while spending nothing - is bounded separately. Otherwise raising the scan
     * profile would also raise how long a broken task can hold the client tick.
     */
    @Test void aTaskThatSpendsNothingIsStoppedRegardlessOfHowLargeTheBudgetIs() {
        var scheduler = new ScanScheduler<String>((owner, error) -> fail(error));
        int[] steps = { 0 };
        scheduler.offer("greedy", Priority.NEAR, 16384, budget -> { steps[0]++; return Result.MORE; });
        scheduler.run(28_000, 128, 8_192);
        assertEquals(ScanScheduler.MAX_IDLE_STEPS, steps[0],
                "a huge budget must not buy a spinning task more of the tick");
    }
}
