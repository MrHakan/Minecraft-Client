package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StableIdsTest {
    private record Entry(String path) { }

    @Test
    void eachEntryIsResolvedOnceAndThenServedFromTheCache() {
        var calls = new AtomicInteger();
        var ids = new StableIds<Entry>(entry -> {
            calls.incrementAndGet();
            return "minecraft:" + entry.path();
        });
        var chest = new Entry("chest");

        String first = ids.of(chest);
        for (int lookup = 0; lookup < 100; lookup++) assertSame(first, ids.of(chest));

        assertEquals("minecraft:chest", first);
        assertEquals(1, calls.get());
    }

    @Test
    void differentEntriesKeepTheirOwnIds() {
        var calls = new AtomicInteger();
        var ids = new StableIds<Entry>(entry -> {
            calls.incrementAndGet();
            return "minecraft:" + entry.path();
        });

        assertEquals("minecraft:chest", ids.of(new Entry("chest")));
        assertEquals("minecraft:barrel", ids.of(new Entry("barrel")));
        assertEquals("minecraft:chest", ids.of(new Entry("chest")));
        assertEquals(2, calls.get());
    }
}
