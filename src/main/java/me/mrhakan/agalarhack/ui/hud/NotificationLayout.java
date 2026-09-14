package me.mrhakan.agalarhack.ui.hud;

import java.util.List;

/** Shared notification bounds for rendering and HUD editing, including short/narrow viewports. */
public record NotificationLayout(int width, int height, int rowHeight, int rows) {
    public static final int GAP = 4;
    public static NotificationLayout measure(List<Integer> textWidths, int screenWidth, int screenHeight, int lineHeight) {
        int rowHeight = Math.max(1, Math.min(128, lineHeight)) + 16;
        int availableWidth = Math.max(0, screenWidth - 16), availableHeight = Math.max(0, screenHeight - 16);
        int rows = Math.min(Math.min(10, textWidths.size()), (availableHeight + GAP) / (rowHeight + GAP));
        if (availableWidth < 20) rows = 0;
        int textWidth = 0;
        for (int index = 0; index < rows; index++) textWidth = Math.max(textWidth, Math.min(4096, textWidths.get(index)));
        int width = Math.min(availableWidth, Math.max(100, textWidth + 18));
        return new NotificationLayout(width, rows == 0 ? 0 : rows * (rowHeight + GAP) - GAP, rowHeight, rows);
    }
}
