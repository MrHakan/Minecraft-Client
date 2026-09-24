package me.mrhakan.agalarhack.ui;

import me.mrhakan.agalarhack.ui.hud.HudText;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudTextTest {
    @Test void leavesTextUntouchedWhenItFits() {
        String text = "AutoGrind ready";
        assertSame(text, HudText.fitWithEllipsis(text, codePointWidth(text), HudTextTest::codePointWidth,
                HudTextTest::prefixByWidth));
    }

    @Test void marksTruncationWithoutSplittingSurrogatePairs() {
        String fitted = HudText.fitWithEllipsis("A🚢BCDE", 4, HudTextTest::codePointWidth, HudTextTest::prefixByWidth);
        assertEquals("A🚢B…", fitted);
        assertEquals(4, fitted.codePointCount(0, fitted.length()));
        for (int index = 0; index < fitted.length(); index++) {
            char current = fitted.charAt(index);
            if (Character.isHighSurrogate(current)) {
                assertTrue(index + 1 < fitted.length() && Character.isLowSurrogate(fitted.charAt(index + 1)));
                index++;
            } else {
                assertFalse(Character.isLowSurrogate(current));
            }
        }
    }

    @Test void handlesWidthsTooSmallForTheMarker() {
        assertEquals("", HudText.fitWithEllipsis("message", 0, HudTextTest::codePointWidth,
                HudTextTest::prefixByWidth));
        assertEquals("…", HudText.fitWithEllipsis("message", 1, HudTextTest::codePointWidth,
                HudTextTest::prefixByWidth));
    }

    private static int codePointWidth(String text) {
        return text.codePointCount(0, text.length());
    }

    private static String prefixByWidth(String text, int maximumWidth) {
        int end = 0;
        int width = 0;
        while (end < text.length()) {
            int codePoint = text.codePointAt(end);
            if (width + 1 > maximumWidth) break;
            width++;
            end += Character.charCount(codePoint);
        }
        return text.substring(0, end);
    }
}
