package me.mrhakan.agalarhack.ui;

import java.util.concurrent.atomic.AtomicInteger;
import me.mrhakan.agalarhack.ui.hud.HudMeasurement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudMeasurementTest {
    @Test void preservesSmallRealDimensionsInsteadOfChangingAnchorGeometry() {
        assertEquals(new HudMeasurement(24,9), HudMeasurement.measure(()->24,()->9,320,240));
    }
    @Test void boundsOversizedAndEmptyProvidersAndEvaluatesOnlyOnce() {
        var calls=new AtomicInteger();
        assertEquals(new HudMeasurement(320,1),HudMeasurement.measure(()->{calls.incrementAndGet();return Integer.MAX_VALUE;},()->0,320,240));
        assertEquals(1,calls.get());
        assertEquals(new HudMeasurement(1,1),HudMeasurement.measure(()->40,()->40,0,0));
    }
    @Test void invalidProvidersReachTheIsolationBoundary() {
        assertThrows(IllegalArgumentException.class,()->HudMeasurement.measure(()->-1,()->10,320,240));
        var failure=new IllegalStateException("broken addon");
        assertSame(failure,assertThrows(IllegalStateException.class,()->HudMeasurement.measure(()->{throw failure;},()->10,320,240)));
    }
}
