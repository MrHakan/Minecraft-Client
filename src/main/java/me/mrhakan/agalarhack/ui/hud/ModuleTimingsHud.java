package me.mrhakan.agalarhack.ui.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.managers.HudLayoutManager;
import me.mrhakan.agalarhack.services.ModuleTimings;
import me.mrhakan.agalarhack.ui.ClientUiTheme;
import net.minecraft.client.Minecraft;

/**
 * Names the modules costing the most tick time.
 *
 * <p>"The client feels slow" is not actionable with nearly fifty modules running; this says which
 * one. Rendering it is also what turns measurement on — {@link ModuleTimings} records only while
 * something asks, so closing this widget stops the {@code nanoTime} pairs on its own without a
 * setting anyone can leave switched on.
 *
 * <p>Hidden by default, like the scanner diagnostics it sits beside.
 */
public final class ModuleTimingsHud {
    private static final String ID = "module_timings";
    private static final int ROWS = 5;

    private final ModuleTimings timings;
    private final HudLayoutManager layout;

    public ModuleTimingsHud(ModuleTimings timings, HudLayoutManager layout) {
        this.timings = timings;
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
        List<ModuleTimings.Entry> slowest = timings.slowest(ROWS);
        List<String> lines = new ArrayList<>(ROWS + 2);
        lines.add(String.format(Locale.ROOT, "MODULE TICK COST / %.0f ticks", (double) ModuleTimings.WINDOW));
        if (slowest.isEmpty()) {
            lines.add("sampling...");
            return lines;
        }
        for (ModuleTimings.Entry entry : slowest) {
            lines.add(String.format(Locale.ROOT, "%s  %.0f us (peak %.0f)",
                    entry.module(), entry.averageMicros(), entry.peakMicros()));
        }
        lines.add(String.format(Locale.ROOT, "All modules: %.0f us/tick", timings.totalAverageMicros()));
        return lines;
    }

    private int width() {
        var font = Minecraft.getInstance().font;
        return lines().stream().mapToInt(font::width).max().orElse(180) + 12;
    }

    private int height() {
        return (ROWS + 2) * (Minecraft.getInstance().font.lineHeight + 2) + 10;
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
                    index == 0 ? ClientUiTheme.ACCENT : ClientUiTheme.TEXT, true);
        }
    }
}
