package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldClockTest {

    @Test
    void tickZeroIsSixInTheMorningNotMidnight() {
        // Every naive conversion is six hours out, and six hours is the difference between safe and
        // everything-is-about-to-spawn.
        assertEquals(6, WorldClock.hour(0));
        assertEquals(0, WorldClock.minute(0));
        assertEquals("Day 1  06:00", WorldClock.formatted(0));
    }

    @Test
    void theClockAdvancesWithTheTicks() {
        assertEquals(7, WorldClock.hour(1000));
        assertEquals(12, WorldClock.hour(6000), "noon");
        assertEquals(18, WorldClock.hour(12000), "sunset");
        assertEquals(0, WorldClock.hour(18000), "midnight");
    }

    @Test
    void minutesComeFromTheSubHourRemainder() {
        assertEquals(30, WorldClock.minute(500));
        assertEquals(0, WorldClock.minute(1000));
        assertEquals(36, WorldClock.minute(2600));
    }

    @Test
    void daysAreCountedFromOne() {
        assertEquals(1, WorldClock.day(0));
        assertEquals(1, WorldClock.day(23_999));
        assertEquals(2, WorldClock.day(24_000));
        assertEquals(13, WorldClock.day(12 * 24_000 + 500));
    }

    @Test
    void theClockWrapsRatherThanRunningPastMidnight() {
        assertEquals(WorldClock.hour(1000), WorldClock.hour(1000 + WorldClock.TICKS_PER_DAY));
        assertEquals(5, WorldClock.hour(23_000), "the last hour before dawn");
    }

    @Test
    void aNegativeTimeStaysOnTheClock() {
        // Some commands can set a negative day time; it must read as a time, not as nonsense.
        assertTrue(WorldClock.timeOfDay(-500) >= 0);
        assertTrue(WorldClock.timeOfDay(-500) < WorldClock.TICKS_PER_DAY);
        assertTrue(WorldClock.hour(-500) >= 0 && WorldClock.hour(-500) < 24);
    }

    @Test
    void nightIsTheWindowWhereTheSkyIsDark() {
        assertFalse(WorldClock.isNight(0));
        assertFalse(WorldClock.isNight(12_999));
        assertTrue(WorldClock.isNight(13_000), "dusk is the boundary, inclusive");
        assertTrue(WorldClock.isNight(18_000));
        assertTrue(WorldClock.isNight(22_999));
        assertFalse(WorldClock.isNight(23_000), "dawn ends it");
    }

    @Test
    void theCountdownPointsAtWhicheverChangeComesNext() {
        assertEquals(13_000, WorldClock.ticksUntilChange(0));
        assertEquals(1_000, WorldClock.ticksUntilChange(12_000));
        assertEquals(10_000, WorldClock.ticksUntilChange(13_000));
        // After dawn it wraps to the following dusk rather than going negative.
        assertEquals(WorldClock.TICKS_PER_DAY - 23_500 + WorldClock.DUSK_TICK,
                WorldClock.ticksUntilChange(23_500));
    }

    @Test
    void theCountdownIsNeverNegativeAnywhereInTheDay() {
        for (long time = 0; time < WorldClock.TICKS_PER_DAY; time += 37) {
            assertTrue(WorldClock.ticksUntilChange(time) > 0, "at " + time);
        }
    }
}
