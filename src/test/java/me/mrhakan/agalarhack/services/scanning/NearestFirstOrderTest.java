package me.mrhakan.agalarhack.services.scanning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NearestFirstOrderTest {
    private record Thing(int id, double distance) { }

    private static List<Thing> ordered(List<Thing> things, double range, int limit) {
        return NearestFirstOrder.of(things, thing -> thing.distance() * thing.distance(), range, limit);
    }

    @Test
    void theNearestComeFirstWhateverOrderTheyArrivedIn() {
        // The measured failure: the nearest arrive last, after a crowd that fills the limit.
        List<Thing> things = new ArrayList<>();
        for (int index = 0; index < 4_500; index++) things.add(new Thing(index, 30 + index % 60));
        for (int index = 0; index < 500; index++) things.add(new Thing(10_000 + index, 2 + index * 0.03));
        List<Thing> kept = ordered(things, 96, 4_096);
        assertEquals(4_096, kept.size());
        Set<Integer> ids = new HashSet<>();
        for (Thing thing : kept) ids.add(thing.id());
        for (int index = 0; index < 500; index++) {
            assertTrue(ids.contains(10_000 + index), "nearest stand " + index + " was left out");
        }
    }

    @Test
    void theKeptPrefixHoldsTheTrueNearestForAnySubscriberCap() {
        Random random = new Random(2010);
        List<Thing> things = new ArrayList<>();
        for (int index = 0; index < 8_000; index++) things.add(new Thing(index, random.nextDouble() * 96));
        List<Thing> kept = ordered(things, 96, 4_096);
        List<Thing> truth = new ArrayList<>(things);
        truth.sort(Comparator.comparingDouble(Thing::distance));
        Set<Thing> keptSet = new HashSet<>(kept);
        // 512 is the largest cap any module offers.
        for (Thing thing : truth.subList(0, 512)) assertTrue(keptSet.contains(thing), "missing " + thing);
        // Bands are ordered: nothing kept is in a later band than anything left out.
        double furthestKept = kept.stream().mapToDouble(Thing::distance).max().orElseThrow();
        double nearestDropped = truth.stream().filter(thing -> !keptSet.contains(thing))
                .mapToDouble(Thing::distance).min().orElseThrow();
        double bandWidth = 96.0 * 96.0 / NearestFirstOrder.BANDS;
        assertTrue(furthestKept * furthestKept - nearestDropped * nearestDropped < bandWidth,
                "kept " + furthestKept + " while dropping " + nearestDropped);
    }

    @Test
    void outOfRangeEntitiesAreLeftOutAndBandsKeepListOrder() {
        List<Thing> things = List.of(new Thing(1, 50), new Thing(2, 97), new Thing(3, 10), new Thing(4, 10.01),
                new Thing(5, 96), new Thing(6, Double.NaN));
        assertEquals(List.of(3, 4, 1, 5), ordered(things, 96, 10).stream().map(Thing::id).toList());
        assertEquals(List.of(3, 4), ordered(things, 96, 2).stream().map(Thing::id).toList());
    }

    @Test
    void nothingToOrder() {
        assertEquals(List.of(), ordered(List.of(), 96, 10));
        assertEquals(List.of(), ordered(List.of(new Thing(1, 1)), 96, 0));
        assertEquals(List.of(), ordered(List.of(new Thing(1, 1)), 0, 10));
    }
}
