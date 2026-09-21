package me.mrhakan.agalarhack.module.render;

import java.util.ArrayList;
import java.util.List;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.ui.hud.HudInfoProvider;
import me.mrhakan.agalarhack.ui.hud.HudLine;

/** Lightweight HUD clock for the current play session. Text is rebuilt at most once per second. */
public class SessionTimer extends Module implements HudInfoProvider {
    private static final int TEXT_COLOR = 0xFFF0F0F0;
    private final List<HudLine> hudLines = new ArrayList<>(1);
    private long startedNanos;
    private long cachedSecond = Long.MIN_VALUE;

    public SessionTimer() {
        super("SessionTimer", Category.RENDER, "Shows how long the current play session has been running");
    }

    @Override public void selfSettings() {
        addBooleanSetting("showSeconds", false, "Show seconds in addition to hours and minutes");
    }

    @Override public void onEnable() { reset(); }
    @Override public void onWorldChanged(boolean worldReady) { if (worldReady) reset(); }
    @Override public void onDisconnect() { reset(); }

    @Override public List<HudLine> getHudLines() {
        if (mc.player == null || mc.level == null) {
            hudLines.clear();
            return hudLines;
        }
        if (startedNanos == 0L) reset();
        long seconds = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000_000L);
        if (seconds == cachedSecond && !hudLines.isEmpty()) return hudLines;
        cachedSecond = seconds;
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        String value = getBooleanSetting("showSeconds", false)
                ? String.format(java.util.Locale.ROOT, "Session: %02d:%02d:%02d", hours, minutes, secs)
                : String.format(java.util.Locale.ROOT, "Session: %02d:%02d", hours, minutes);
        hudLines.clear();
        hudLines.add(new HudLine(value, TEXT_COLOR));
        return hudLines;
    }

    private void reset() {
        startedNanos = System.nanoTime();
        cachedSecond = Long.MIN_VALUE;
        hudLines.clear();
    }
}
