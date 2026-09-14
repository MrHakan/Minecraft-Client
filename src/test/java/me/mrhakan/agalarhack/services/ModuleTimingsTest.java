package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleTimingsTest {

    private static ModuleTimings recording() {
        ModuleTimings timings = new ModuleTimings();
        timings.requestRecording();
        return timings;
    }

    @Test
    void recordsNothingUntilSomethingAsks() {
        ModuleTimings timings = new ModuleTimings();
        assertFalse(timings.isRecording());
        timings.record("KillAura", 5_000);
        assertEquals(List.of(), timings.slowest(5));
    }

    @Test
    void recordsOnceAsked() {
        ModuleTimings timings = recording();
        assertTrue(timings.isRecording());
        timings.record("KillAura", 5_000);
        assertEquals(1, timings.slowest(5).size());
        assertEquals(5.0, timings.slowest(5).get(0).averageMicros(), 1e-9, "nanos must be reported as micros");
    }

    @Test
    void recordingExpiresOnItsOwnAndDropsTheHistory() {
        ModuleTimings timings = recording();
        timings.record("KillAura", 5_000);
        for (int tick = 0; tick <= ModuleTimings.IDLE_TICKS; tick++) timings.beginTick();
        assertFalse(timings.isRecording(), "nothing asked for " + ModuleTimings.IDLE_TICKS + " ticks");
        timings.beginTick();
        assertEquals(0, timings.trackedModules(), "stale figures must not linger as if still current");
    }

    @Test
    void askingAgainKeepsItAlive() {
        ModuleTimings timings = recording();
        for (int tick = 0; tick < ModuleTimings.IDLE_TICKS * 3; tick++) {
            timings.beginTick();
            timings.requestRecording();
            assertTrue(timings.isRecording(), "tick " + tick);
        }
    }

    @Test
    void rankingIsWorstFirst() {
        ModuleTimings timings = recording();
        timings.record("Cheap", 1_000);
        timings.record("Expensive", 9_000);
        timings.record("Middling", 4_000);
        assertEquals(List.of("Expensive", "Middling", "Cheap"),
                timings.slowest(5).stream().map(ModuleTimings.Entry::module).toList());
    }

    @Test
    void tiesBreakByNameSoTheListDoesNotShuffle() {
        ModuleTimings timings = recording();
        timings.record("Zebra", 1_000);
        timings.record("Alpha", 1_000);
        assertEquals(List.of("Alpha", "Zebra"),
                timings.slowest(5).stream().map(ModuleTimings.Entry::module).toList());
    }

    @Test
    void limitIsRespectedAndNeverNegative() {
        ModuleTimings timings = recording();
        for (int index = 0; index < 10; index++) timings.record("M" + index, (index + 1) * 1_000);
        assertEquals(3, timings.slowest(3).size());
        assertEquals(0, timings.slowest(0).size());
        assertEquals(0, timings.slowest(-5).size());
        assertEquals(10, timings.slowest(99).size());
    }

    @Test
    void windowKeepsOnlyRecentTicks() {
        ModuleTimings timings = recording();
        // A long-gone spike must not still be the reported average.
        timings.record("M", 100_000);
        for (int tick = 0; tick < ModuleTimings.WINDOW; tick++) timings.record("M", 1_000);
        ModuleTimings.Entry entry = timings.slowest(1).get(0);
        assertEquals(1.0, entry.averageMicros(), 1e-9);
        assertEquals(1.0, entry.peakMicros(), 1e-9, "the spike must have aged out of the peak too");
        assertEquals(ModuleTimings.WINDOW, entry.ticks());
    }

    @Test
    void peakIsReportedAlongsideTheAverage() {
        ModuleTimings timings = recording();
        timings.record("M", 1_000);
        timings.record("M", 9_000);
        ModuleTimings.Entry entry = timings.slowest(1).get(0);
        assertEquals(5.0, entry.averageMicros(), 1e-9);
        assertEquals(9.0, entry.peakMicros(), 1e-9);
    }

    @Test
    void trackedNamesAreBounded() {
        ModuleTimings timings = recording();
        for (int index = 0; index < ModuleTimings.MAX_MODULES * 2; index++) timings.record("M" + index, 1_000);
        assertEquals(ModuleTimings.MAX_MODULES, timings.trackedModules());
    }

    @Test
    void forgettingAModuleRemovesItFromTheReport() {
        ModuleTimings timings = recording();
        timings.record("KillAura", 5_000);
        timings.record("AutoWalk", 1_000);
        timings.forget("KillAura");
        assertEquals(List.of("AutoWalk"),
                timings.slowest(5).stream().map(ModuleTimings.Entry::module).toList());
    }

    @Test
    void aModuleThatStopsTickingDropsOffTheList() {
        ModuleTimings timings = recording();
        timings.record("StillOn", 1_000);
        timings.record("SwitchedOff", 9_000);
        timings.beginTick();
        timings.requestRecording();
        timings.record("StillOn", 1_000);
        timings.beginTick();
        assertEquals(List.of("StillOn"),
                timings.slowest(5).stream().map(ModuleTimings.Entry::module).toList(),
                "a module that costs nothing must not stay on screen as the expensive one");
    }

    @Test
    void aModuleStillTickingSurvivesEveryTick() {
        ModuleTimings timings = recording();
        for (int tick = 0; tick < 10; tick++) {
            timings.requestRecording();
            timings.record("M", 1_000);
            timings.beginTick();
            assertEquals(1, timings.trackedModules(), "dropped on tick " + tick);
        }
    }

    @Test
    void totalIsTheSumOfTheAverages() {
        ModuleTimings timings = recording();
        timings.record("A", 1_000);
        timings.record("A", 3_000);
        timings.record("B", 5_000);
        assertEquals(7.0, timings.totalAverageMicros(), 1e-9);
    }

    @Test
    void rejectsNonsenseWithoutPoisoningTheStats() {
        ModuleTimings timings = recording();
        timings.record(null, 1_000);
        timings.record("M", -1);
        assertEquals(0, timings.trackedModules());
    }
}
