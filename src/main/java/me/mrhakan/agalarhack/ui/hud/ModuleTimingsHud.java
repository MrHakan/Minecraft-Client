package me.mrhakan.agalarhack.ui.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.managers.HudLayoutManager;
import me.mrhakan.agalarhack.services.ModuleTimings;
import me.mrhakan.agalarhack.services.OverlayTimings;
import me.mrhakan.agalarhack.ui.ClientUiTheme;
import net.minecraft.client.Minecraft;

/**
 * Names the modules costing the most tick time, and the world overlays costing the most frame time.
 *
 * <p>"The client feels slow" is not actionable with nearly fifty modules running; this says which
 * one. Rendering it is also what turns measurement on — {@link ModuleTimings} and
 * {@link OverlayTimings} record only while something asks, so closing this widget stops the
 * {@code nanoTime} pairs on its own without a setting anyone can leave switched on.
 *
 * <p>Hidden by default, like the scanner diagnostics it sits beside.
 */
public final class ModuleTimingsHud {
    private static final String ID = "module_timings";
    private static final int ROWS = 5;
    private static final int OVERLAY_ROWS = 3;
    /** Lines before the overlay section: heading, module rows, module total. */
    private static final int OVERLAY_HEADING = ROWS + 2;

    private final ModuleTimings timings;
    private final OverlayTimings overlays;
    private final HudLayoutManager layout;

    public ModuleTimingsHud(ModuleTimings timings, OverlayTimings overlays, HudLayoutManager layout) {
        this.timings = timings;
        this.overlays = overlays;
        this.layout = layout;
    }

    public void register(HudRegistry registry) {
        registry.register(new HudRegistry.Component(ID, "Module Timings (developer)", this::width, this::height, this::render),
                new HudLayoutManager.WidgetState(HudLayoutManager.Anchor.TOP_LEFT, 8, 260, false));
    }

    /**
     * The first frames after this is shown have no samples yet, which would otherwise look like a
     * broken widget rather than one that is about to fill in.
     */
    private List<String> lines() {
        timings.requestRecording();
        overlays.requestRecording();
        List<ModuleTimings.Entry> slowest = timings.slowest(ROWS);
        List<String> lines = new ArrayList<>(OVERLAY_HEADING + OVERLAY_ROWS + 2);
        lines.add(String.format(Locale.ROOT, "MODULE TICK COST / %.0f ticks", (double) ModuleTimings.WINDOW));
        if (slowest.isEmpty()) {
            lines.add("sampling...");
        } else {
            for (ModuleTimings.Entry entry : slowest) {
                lines.add(String.format(Locale.ROOT, "%s  %.0f us (peak %.0f)",
                        entry.module(), entry.averageMicros(), entry.peakMicros()));
            }
            lines.add(String.format(Locale.ROOT, "All modules: %.0f us/tick", timings.totalAverageMicros()));
        }
        // A fixed position keeps the overlay heading from jumping while the module rows fill in.
        while (lines.size() < OVERLAY_HEADING) lines.add("");
        lines.add(String.format(Locale.ROOT, "OVERLAY FRAME COST / %.0f frames", (double) OverlayTimings.WINDOW));
        List<ModuleTimings.Entry> costliest = overlays.slowest(OVERLAY_ROWS);
        if (costliest.isEmpty()) {
            lines.add("no overlay drawing");
            return lines;
        }
        for (ModuleTimings.Entry entry : costliest) {
            lines.add(String.format(Locale.ROOT, "%s  %.0f us (peak %.0f)",
                    entry.module(), entry.averageMicros(), entry.peakMicros()));
        }
        lines.add(String.format(Locale.ROOT, "All overlays: %.0f us/frame", overlays.totalAverageMicros()));
        return lines;
    }

    private int width() {
        var font = Minecraft.getInstance().font;
        return lines().stream().mapToInt(font::width).max().orElse(180) + 12;
    }

    private int height() {
        return (OVERLAY_HEADING + OVERLAY_ROWS + 2) * (Minecraft.getInstance().font.lineHeight + 2) + 10;
    }

    private void render(ClientEvents.HudRender event) {
        var font = Minecraft.getInstance().font;
        var graphics = event.graphics();
        List<String> lines = lines();
        int width = lines.stream().mapToInt(font::width).max().orElse(180) + 12;
        int height = height();
        int x = layout.resolveX(ID, graphics.guiWidth(), width);
        int y = layout.resolveY(ID, graphics.guiHeight(), height);
        graphics.fill(x, y, x + width, y + height, ClientUiTheme.PANEL);
        for (int index = 0; index < lines.size(); index++) {
            graphics.text(font, lines.get(index), x + 6, y + 5 + index * (font.lineHeight + 2),
                    index == 0 || index == OVERLAY_HEADING ? ClientUiTheme.ACCENT : ClientUiTheme.TEXT, true);
        }
    }
}
