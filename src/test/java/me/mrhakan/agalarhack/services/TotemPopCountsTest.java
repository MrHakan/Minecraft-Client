package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TotemPopCountsTest {
    @Test void countingStartsAtOneAndAccumulates() {
        var counts = new TotemPopCounts(TotemPopCounts.DEFAULT_CAPACITY, 0);
        assertEquals(1, counts.pop("Steve", 0));
        assertEquals(2, counts.pop("Steve", 10));
        assertEquals(2, counts.count("Steve", 20));
    }

    @Test void playersAreCountedSeparately() {
        var counts = new TotemPopCounts(TotemPopCounts.DEFAULT_CAPACITY, 0);
        counts.pop("Steve", 0);
        counts.pop("Steve", 1);
        counts.pop("Alex", 2);
        assertEquals(2, counts.count("Steve", 3));
        assertEquals(1, counts.count("Alex", 3));
    }

    @Test void anUnknownPlayerCountsAsZero() {
        var counts = new TotemPopCounts(TotemPopCounts.DEFAULT_CAPACITY, 0);
        assertEquals(0, counts.count("Nobody", 0));
        assertEquals(0, counts.count(null, 0));
    }

    @Test void deathResetsTheTally() {
        var counts = new TotemPopCounts(TotemPopCounts.DEFAULT_CAPACITY, 0);
        counts.pop("Steve", 0);
        counts.pop("Steve", 1);
        counts.reset("Steve");
        assertEquals(0, counts.count("Steve", 2));
        assertEquals(1, counts.pop("Steve", 3));
    }

    @Test void aPlayerNotSeenForTheTimeoutIsForgotten() {
        var counts = new TotemPopCounts(TotemPopCounts.DEFAULT_CAPACITY, 1000);
        counts.pop("Steve", 0);
        assertEquals(1, counts.count("Steve", 900));
        assertEquals(0, counts.count("Steve", 1500), "a returning player is not still on one totem");
    }

    @Test void aZeroTimeoutKeepsEverything() {
        var counts = new TotemPopCounts(TotemPopCounts.DEFAULT_CAPACITY, 0);
        counts.pop("Steve", 0);
        assertEquals(1, counts.count("Steve", Long.MAX_VALUE / 2));
    }

    @Test void eachPopRefreshesTheTimeout() {
        var counts = new TotemPopCounts(TotemPopCounts.DEFAULT_CAPACITY, 1000);
        counts.pop("Steve", 0);
        counts.pop("Steve", 900);
        assertEquals(2, counts.count("Steve", 1500));
    }

    @Test void trackingIsBoundedAndDropsLeastRecentlyUsed() {
        var counts = new TotemPopCounts(2, 0);
        counts.pop("A", 0);
        counts.pop("B", 1);
        counts.count("A", 2);
        counts.pop("C", 3);
        assertEquals(2, counts.tracked());
        assertEquals(0, counts.count("B", 4));
        assertEquals(1, counts.count("A", 4));
    }

    @Test void blankNamesAreIgnoredRatherThanTracked() {
        var counts = new TotemPopCounts(TotemPopCounts.DEFAULT_CAPACITY, 0);
        assertEquals(0, counts.pop("", 0));
        assertEquals(0, counts.pop(null, 0));
        assertEquals(0, counts.tracked());
    }

    @Test void capacityAndTimeoutAreBounded() {
        assertDoesNotThrow(() -> new TotemPopCounts(0, -5));
        var counts = new TotemPopCounts(Integer.MAX_VALUE, -1);
        counts.pop("Steve", 0);
        assertEquals(1, counts.count("Steve", Long.MAX_VALUE / 2), "a negative timeout must not expire everything");
    }

    @Test void clearDropsEveryTally() {
        var counts = new TotemPopCounts(TotemPopCounts.DEFAULT_CAPACITY, 0);
        counts.pop("Steve", 0);
        counts.clear();
        assertEquals(0, counts.tracked());
    }
}
