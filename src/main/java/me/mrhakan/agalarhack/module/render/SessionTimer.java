package me.mrhakan.agalarhack.module.render;

import java.util.ArrayList;
import java.util.List;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.ui.hud.HudInfoProvider;
import me.mrhakan.agalarhack.ui.hud.HudLine;

/** Lightweight HUD clock for the current play session. */
public class SessionTimer extends Module implements HudInfoProvider {
    private static final int TEXT_COLOR = 0xFFF0F0F0;
    private final List<HudLine> hudLines = new ArrayList<>(1);
    private final StringBuilder text = new StringBuilder(24);
    private long startedNanos;
    private long cachedBucket = Long.MIN_VALUE;
    private boolean cachedShowSeconds;

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
        boolean showSeconds = getBooleanSetting("showSeconds", false);
        // Minute-only mode does not need to rebuild identical HUD text sixty times per minute.
        long bucket = showSeconds ? seconds : seconds / 60L;
        if (bucket == cachedBucket && showSeconds == cachedShowSeconds && !hudLines.isEmpty()) return hudLines;
        cachedBucket = bucket;
        cachedShowSeconds = showSeconds;
        rebuildText(seconds, showSeconds);
        return hudLines;
    }

    private void rebuildText(long seconds, boolean showSeconds) {
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        text.setLength(0);
        text.append("Session: ");
        appendTwoDigits(text, hours);
        text.append(':'); appendTwoDigits(text, minutes);
        if (showSeconds) { text.append(':'); appendTwoDigits(text, secs); }
        hudLines.clear();
        hudLines.add(new HudLine(text.toString(), TEXT_COLOR));
    }

    private static void appendTwoDigits(StringBuilder out, long value) {
        if (value < 10L) out.append('0');
        out.append(value);
    }

    private void reset() {
        startedNanos = System.nanoTime();
        cachedBucket = Long.MIN_VALUE;
        cachedShowSeconds = false;
        hudLines.clear();
    }
}
