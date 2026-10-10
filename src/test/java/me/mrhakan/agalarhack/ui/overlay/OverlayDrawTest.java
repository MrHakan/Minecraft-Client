package me.mrhakan.agalarhack.ui.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** ESP labels stopped using {@code String.format} in 2.0.09; the text must be exactly what it was. */
class OverlayDrawTest {
    private static void assertTenths(double value) {
        assertEquals(String.format(Locale.ROOT, "%.1f", value),
                OverlayDraw.appendTenths(new StringBuilder(), value).toString(), "value " + value);
    }

    @Test
    void tenthsAreWhatStringFormatWrites() {
        for (double value : new double[] { 0.0, -0.0, 0.04, 0.05, 0.15, 0.25, 0.35, 0.95, 1.0, 2.25, 9.95, 9.96,
                63.99, 64.0, 99.95, 123.45, 999999.95, 1.0E6, 5.0E7, -1.0, -0.04, Double.NaN,
                Double.POSITIVE_INFINITY, Double.MIN_VALUE }) {
            assertTenths(value);
        }
        Random random = new Random(209);
        for (int index = 0; index < 100_000; index++) {
            // Distances and health both arrive as floats widened to double.
            assertTenths((float) (random.nextDouble() * 300.0));
            assertTenths(random.nextInt(4000) / 20.0f);
        }
        for (int tenths = 0; tenths < 20_000; tenths++) {
            assertTenths(tenths / 10.0);
            assertTenths(tenths / 10.0 + 0.05);
            assertTenths((float) (tenths / 10.0 + 0.05));
        }
    }
}
