package me.mrhakan.agalarhack.services.scanning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class SharedEntityWalkTest {
    /** A stand-in entity: its distance from the player is fixed so ranges are exact. */
    private record Thing(int id, double distance) { }

    private static List<Thing> things(int count) {
        List<Thing> list = new ArrayList<>();
        for (int index = 0; index < count; index++) list.add(new Thing(index, index + 1.0));
        return list;
    }

    private final ScanScheduler<Object> scheduler = new ScanScheduler<>((owner, error) -> {
        throw new AssertionError("the walk task itself must never fail", error);
    });

    private void run(SharedEntityWalk<String, Thing> walk, List<Thing> entities, int entityBudget, BooleanSupplier valid) {
        scheduler.offer(walk, walk.priority(), walk.maximumSteps(),
                walk.start(entities.iterator(), thing -> thing.distance() * thing.distance(), Thing::id, valid));
        scheduler.run(0, 0, entityBudget);
    }

    private void run(SharedEntityWalk<String, Thing> walk, List<Thing> entities, int entityBudget) {
        run(walk, entities, entityBudget, () -> true);
    }

    private static SharedEntityWalk<String, Thing> walk(List<String> failures) {
        return new SharedEntityWalk<>(4096, (owner, error) -> failures.add(owner));
    }

    @Test
    void oneWalkServesEverySubscriberAndAnEntityCostsOneUnit() {
        // The ceiling this removes: three consumers each walking ten entities used to cost thirty.
        var walk = walk(new ArrayList<>());
        var a = new AtomicReference<List<Thing>>();
        var b = new AtomicReference<List<Thing>>();
        var c = new AtomicReference<List<Thing>>();
        walk.subscribe("a", ScanScheduler.Priority.NEAR, 64, 100, thing -> thing, a::set);
        walk.subscribe("b", ScanScheduler.Priority.NEAR, 64, 100, thing -> thing, b::set);
        walk.subscribe("c", ScanScheduler.Priority.NEAR, 64, 100, thing -> thing, c::set);

        run(walk, things(10), 10);

        assertEquals(10, a.get().size());
        assertEquals(10, b.get().size());
        assertEquals(10, c.get().size());
        assertEquals(10, scheduler.lastUsage().entities());
    }

    @Test
    void eachSubscriberOnlySeesEntitiesInsideItsOwnRange() {
        var walk = walk(new ArrayList<>());
        var near = new AtomicReference<List<Thing>>();
        var far = new AtomicReference<List<Thing>>();
        var nearCalls = new AtomicInteger();
        walk.subscribe("near", ScanScheduler.Priority.NEAR, 64, 3.0, thing -> { nearCalls.incrementAndGet(); return thing; }, near::set);
        walk.subscribe("far", ScanScheduler.Priority.NEAR, 64, 8.0, thing -> thing, far::set);

        run(walk, things(10), 100);

        assertEquals(List.of(0, 1, 2), near.get().stream().map(Thing::id).toList());
        assertEquals(3, nearCalls.get(), "the filter ran for an entity outside the subscriber's range");
        assertEquals(8, far.get().size());
    }

    @Test
    void resultsAreTheNearestFirstAndCappedPerSubscriber() {
        var walk = walk(new ArrayList<>());
        var result = new AtomicReference<List<Thing>>();
        walk.subscribe("capped", ScanScheduler.Priority.NEAR, 2, 100, thing -> thing, result::set);
        // Walked farthest first, so the cap has to replace what it kept.
        var reversed = new ArrayList<>(things(6));
        java.util.Collections.reverse(reversed);

        run(walk, reversed, 100);

        assertEquals(List.of(0, 1), result.get().stream().map(Thing::id).toList());
    }

    @Test
    void aNullSelectionSkipsTheEntity() {
        var walk = walk(new ArrayList<>());
        var result = new AtomicReference<List<Thing>>();
        walk.subscribe("even", ScanScheduler.Priority.NEAR, 64, 100, thing -> thing.id() % 2 == 0 ? thing : null, result::set);

        run(walk, things(6), 100);

        assertEquals(List.of(0, 2, 4), result.get().stream().map(Thing::id).toList());
    }

    @Test
    void subscriptionsMustBeRepeatedEveryTick() {
        // Work from a module that stopped asking - because it was disabled - must not carry on.
        var walk = walk(new ArrayList<>());
        var calls = new AtomicInteger();
        walk.subscribe("once", ScanScheduler.Priority.NEAR, 64, 100, thing -> thing, result -> calls.incrementAndGet());

        run(walk, things(3), 100);
        assertTrue(walk.isEmpty());
        run(walk, things(3), 100);

        assertEquals(1, calls.get());
    }

    @Test
    void aLaterSubscriptionFromTheSameOwnerReplacesTheEarlierOne() {
        var walk = walk(new ArrayList<>());
        var first = new AtomicBoolean();
        var second = new AtomicReference<List<Thing>>();
        walk.subscribe("module", ScanScheduler.Priority.NEAR, 64, 100, thing -> thing, result -> first.set(true));
        walk.subscribe("module", ScanScheduler.Priority.NEAR, 64, 2.0, thing -> thing, second::set);

        run(walk, things(5), 100);

        assertFalse(first.get());
        assertEquals(2, second.get().size());
    }

    @Test
    void aCancelledSubscriberReceivesNothing() {
        var walk = walk(new ArrayList<>());
        var cancelled = new AtomicBoolean();
        var kept = new AtomicReference<List<Thing>>();
        walk.subscribe("cancelled", ScanScheduler.Priority.NEAR, 64, 100, thing -> thing, result -> cancelled.set(true));
        walk.subscribe("kept", ScanScheduler.Priority.NEAR, 64, 100, thing -> thing, kept::set);
        walk.cancel("cancelled");

        run(walk, things(4), 100);

        assertFalse(cancelled.get());
        assertEquals(4, kept.get().size());
    }

    @Test
    void cancellingDuringAWalkStillStopsThatResult() {
        // A filter can disable another module mid-walk, and that module's onDisable cancels its scan.
        var walk = walk(new ArrayList<>());
        var victim = new AtomicBoolean();
        walk.subscribe("trigger", ScanScheduler.Priority.NEAR, 64, 100, thing -> {
            if (thing.id() == 1) walk.cancel("victim");
            return thing;
        }, result -> { });
        walk.subscribe("victim", ScanScheduler.Priority.NEAR, 64, 100, thing -> thing, result -> victim.set(true));

        run(walk, things(4), 100);

        assertFalse(victim.get());
    }

    @Test
    void aThrowingFilterCostsOnlyItsOwnSubscriber() {
        var failures = new ArrayList<String>();
        var walk = walk(failures);
        var broken = new AtomicBoolean();
        var healthy = new AtomicReference<List<Thing>>();
        walk.subscribe("broken", ScanScheduler.Priority.NEAR, 64, 100, thing -> {
            if (thing.id() == 2) throw new IllegalStateException("filter bug");
            return thing;
        }, result -> broken.set(true));
        walk.subscribe("healthy", ScanScheduler.Priority.NEAR, 64, 100, thing -> thing, healthy::set);

        run(walk, things(5), 100);

        assertEquals(List.of("broken"), failures);
        assertFalse(broken.get(), "a subscriber that failed must not publish a partial result");
        assertEquals(5, healthy.get().size());
    }

    @Test
    void aThrowingSinkIsReportedWithoutStoppingTheOthers() {
        var failures = new ArrayList<String>();
        var walk = walk(failures);
        var healthy = new AtomicReference<List<Thing>>();
        walk.subscribe("broken", ScanScheduler.Priority.NEAR, 64, 100, thing -> thing,
                result -> { throw new IllegalStateException("sink bug"); });
        walk.subscribe("healthy", ScanScheduler.Priority.NEAR, 64, 100, thing -> thing, healthy::set);

        run(walk, things(3), 100);

        assertEquals(List.of("broken"), failures);
        assertEquals(3, healthy.get().size());
    }

    @Test
    void aReplacedWorldEmptiesEveryResult() {
        var walk = walk(new ArrayList<>());
        var a = new AtomicReference<List<Thing>>();
        var b = new AtomicReference<List<Thing>>();
        walk.subscribe("a", ScanScheduler.Priority.NEAR, 64, 100, thing -> thing, a::set);
        walk.subscribe("b", ScanScheduler.Priority.NEAR, 64, 100, thing -> thing, b::set);
        var steps = new AtomicInteger();

        run(walk, things(10), 100, () -> steps.incrementAndGet() < 4);

        assertEquals(List.of(), a.get());
        assertEquals(List.of(), b.get());
    }

    @Test
    void runningOutOfBudgetPublishesTheWalkedPrefix() {
        var walk = walk(new ArrayList<>());
        var result = new AtomicReference<List<Thing>>();
        walk.subscribe("a", ScanScheduler.Priority.NEAR, 64, 100, thing -> thing, result::set);

        run(walk, things(10), 4);

        assertEquals(List.of(0, 1, 2, 3), result.get().stream().map(Thing::id).toList());
        assertEquals(4, scheduler.lastUsage().entities());
    }

    @Test
    void theObservationCapBoundsTheWalk() {
        var walk = new SharedEntityWalk<String, Thing>(3, (owner, error) -> { });
        var result = new AtomicReference<List<Thing>>();
        walk.subscribe("a", ScanScheduler.Priority.NEAR, 64, 100, thing -> thing, result::set);

        run(walk, things(10), 100);

        assertEquals(3, result.get().size());
        assertEquals(3, scheduler.lastUsage().entities());
    }

    @Test
    void theWalkRunsAtTheMostUrgentRequestedPriority() {
        var walk = walk(new ArrayList<>());
        assertEquals(ScanScheduler.Priority.BACKGROUND, walk.priority());
        walk.subscribe("background", ScanScheduler.Priority.BACKGROUND, 8, 10, thing -> thing, result -> { });
        assertEquals(ScanScheduler.Priority.BACKGROUND, walk.priority());
        walk.subscribe("focused", ScanScheduler.Priority.FOCUSED, 8, 10, thing -> thing, result -> { });
        assertEquals(ScanScheduler.Priority.FOCUSED, walk.priority());
        walk.subscribe("near", ScanScheduler.Priority.NEAR, 8, 10, thing -> thing, result -> { });
        assertEquals(ScanScheduler.Priority.NEAR, walk.priority());
    }

    @Test
    void clearingDropsPendingAndRunningSubscriptions() {
        var walk = walk(new ArrayList<>());
        var result = new AtomicReference<List<Thing>>();
        walk.subscribe("a", ScanScheduler.Priority.NEAR, 64, 100, thing -> thing, result::set);
        var task = walk.start(things(3).iterator(), thing -> thing.distance() * thing.distance(), Thing::id, () -> true);
        walk.clear();

        scheduler.offer(walk, ScanScheduler.Priority.NEAR, walk.maximumSteps(), task);
        scheduler.run(0, 0, 100);

        assertNull(result.get());
        assertTrue(walk.isEmpty());
    }
}
