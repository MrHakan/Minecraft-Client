package me.mrhakan.agalarhack.ui;

import java.util.List;
import me.mrhakan.agalarhack.ui.hud.NotificationLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationLayoutTest {
    @Test void capsRowsAndWidthsToViewportWithoutTrailingGap() {
        var layout = NotificationLayout.measure(List.of(60, 600, 120), 200, 70, 9);
        assertEquals(2, layout.rows());
        assertEquals(184, layout.width());
        assertEquals(54, layout.height());
        assertTrue(layout.height() <= 70 - 16);
    }
    @Test void tinyAndEmptyViewportsProduceNoRows() {
        assertEquals(0, NotificationLayout.measure(List.of(100), 15, 20, 9).rows());
        assertEquals(0, NotificationLayout.measure(List.of(), 320, 240, 9).height());
    }
}
