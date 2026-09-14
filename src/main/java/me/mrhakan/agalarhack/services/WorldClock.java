package me.mrhakan.agalarhack.services;

import java.util.Locale;

/**
 * Minecraft's day time as a clock a person can read.
 *
 * <p>The raw value is ticks since the start of day one, where 0 is <b>06:00</b> rather than midnight.
 * That offset is the whole reason this exists: every naive conversion is six hours out, and six hours
 * is the difference between "safe" and "everything is about to spawn".
 *
 * <p>Free of Minecraft types so the offset, the wrap and the day count are unit tested directly.
 */
public final class WorldClock {
    private WorldClock() { }

    public static final long TICKS_PER_DAY = 24_000L;
    /** Tick 0 is 06:00, so six hours are added before the clock reads correctly. */
    public static final int DAWN_HOUR = 6;
    /** Monsters spawn in the open between these; matches vanilla's own day/night boundary. */
    public static final long DUSK_TICK = 13_000L;
    public static final long DAWN_TICK = 23_000L;

    /** @return the in-game day number, counting the first day as 1 */
    public static long day(long dayTime) {
        return Math.floorDiv(dayTime, TICKS_PER_DAY) + 1;
    }

    /** Ticks since this day began; always in {@code [0, 24000)} even for a negative input. */
    public static long timeOfDay(long dayTime) {
        return Math.floorMod(dayTime, TICKS_PER_DAY);
    }

    public static int hour(long dayTime) {
        return (int) ((timeOfDay(dayTime) / 1000 + DAWN_HOUR) % 24);
    }

    public static int minute(long dayTime) {
        return (int) (timeOfDay(dayTime) % 1000 * 60 / 1000);
    }

    /** @return {@code Day 12  07:30} */
    public static String formatted(long dayTime) {
        return String.format(Locale.ROOT, "Day %d  %02d:%02d", day(dayTime), hour(dayTime), minute(dayTime));
    }

    /**
     * Whether the sky is dark enough for hostile mobs to spawn in the open.
     *
     * <p>This is about the sky only. What actually spawns also depends on light, biome and the mob
     * cap, none of which this claims to know — the same line SpawnESP draws.
     */
    public static boolean isNight(long dayTime) {
        long time = timeOfDay(dayTime);
        return time >= DUSK_TICK && time < DAWN_TICK;
    }

    /** Ticks until the next dusk or dawn, whichever comes first. */
    public static long ticksUntilChange(long dayTime) {
        long time = timeOfDay(dayTime);
        if (time < DUSK_TICK) return DUSK_TICK - time;
        if (time < DAWN_TICK) return DAWN_TICK - time;
        return TICKS_PER_DAY - time + DUSK_TICK;
    }
}
