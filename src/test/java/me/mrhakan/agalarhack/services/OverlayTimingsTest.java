package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class OverlayTimingsTest {

    private static OverlayTimings recording() {
        OverlayTimings timings = new OverlayTimings();
        timings.requestRecording();
        return timings;
    }

    @Test
    void measuresNothingUntilSomethingAsks() {
        OverlayTimings timings = new OverlayTimings();
        assertNull(timings.beginFrame(3), "an unwatched frame must not allocate or time anything");
        assertEquals(List.of(), timings.slowest(5));
    }

    @Test
    void labelsAndLinesOfOneFrameAreOneSample() {
        OverlayTimings timings = recording();
        OverlayTimings.Frame frame = timings.beginFrame(2);
        frame.drawn(0, "Nametags");
        frame.add(0, 3_000);  // labels, while the frame is collected
        frame.add(0, 2_000);  // lines, in the deferred pass
        assertEquals(List.of(), timings.slowest(5), "a frame is only complete once the next one starts");
        timings.beginFrame(2);
        ModuleTimings.Entry entry = timings.slowest(5).get(0);
        assertEquals("Nametags", entry.module());
        assertEquals(5.0, entry.averageMicros(), 1e-9);
        assertEquals(1, entry.ticks(), "two passes of one frame are one sample, not two");
    }

    @Test
    void anOverlayThatStopsDrawingLeavesTheFigures() {
        OverlayTimings timings = recording();
        OverlayTimings.Frame frame = timings.beginFrame(2);
        frame.drawn(0, "ESP");
        frame.drawn(1, "Tracers");
        frame.add(0, 4_000);
        frame.add(1, 1_000);
        frame = timings.beginFrame(2);
        assertEquals(2, timings.slowest(5).size());
        frame.drawn(1, "Tracers");  // ESP was switched off
        frame.add(1, 1_000);
        timings.beginFrame(2);
        timings.beginFrame(2);
        assertEquals(List.of("Tracers"), timings.slowest(5).stream().map(ModuleTimings.Entry::module).toList(),
                "an overlay that no longer draws costs nothing and must not be named as expensive");
    }

    @Test
    void recordingExpiresAfterTheIdleFrames() {
        OverlayTimings timings = recording();
        OverlayTimings.Frame frame = timings.beginFrame(1);
        frame.drawn(0, "ESP");
        frame.add(0, 1_000);
        for (int index = 0; index < OverlayTimings.IDLE_FRAMES; index++) timings.beginFrame(1);
        assertNull(timings.beginFrame(1), "nothing asked for " + OverlayTimings.IDLE_FRAMES + " frames");
        assertEquals(List.of(), timings.slowest(5), "stale figures must not linger as if still current");
        timings.requestRecording();
        assertNotNull(timings.beginFrame(1));
    }

    @Test
    void theWindowCountsFrames() {
        OverlayTimings timings = recording();
        for (int index = 0; index <= OverlayTimings.WINDOW + 10; index++) {
            OverlayTimings.Frame frame = timings.beginFrame(1);
            frame.drawn(0, "ESP");
            frame.add(0, index < 10 ? 1_000_000 : 1_000);
        }
        ModuleTimings.Entry entry = timings.slowest(1).get(0);
        assertEquals(OverlayTimings.WINDOW, entry.ticks());
        assertEquals(1.0, entry.averageMicros(), 1e-9, "the expensive first frames have left the window");
        assertTrue(timings.totalAverageMicros() > 0);
    }

    @Test
    void aSecondPassAfterTheFrameClosedIsIgnored() {
        OverlayTimings timings = recording();
        OverlayTimings.Frame first = timings.beginFrame(1);
        first.drawn(0, "ESP");
        first.add(0, 1_000);
        timings.beginFrame(1);
        first.add(0, 50_000);  // a late deferred pass of an already closed frame
        timings.beginFrame(1);
        assertEquals(1.0, timings.slowest(1).get(0).averageMicros(), 1e-9);
    }
}
