package me.mrhakan.agalarhack.ui.hud;

import java.util.List;
import java.util.Locale;
import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.managers.HudLayoutManager;
import me.mrhakan.agalarhack.managers.ModuleManager;
import me.mrhakan.agalarhack.module.render.BlockESP;
import me.mrhakan.agalarhack.module.render.StorageESP;
import me.mrhakan.agalarhack.services.ScannerService;
import me.mrhakan.agalarhack.ui.ClientUiTheme;
import net.minecraft.client.Minecraft;

/** Opt-in developer diagnostics from existing counters; never initiates world discovery. */
public final class ScannerDebugHud {
    private static final String ID = "scanner_debug";
    private final ScannerService scanners;
    private final HudLayoutManager layout;
    private final ModuleManager modules;
    public ScannerDebugHud(ScannerService scanners, HudLayoutManager layout, ModuleManager modules) {
        this.scanners = scanners; this.layout = layout; this.modules = modules;
    }
    public void register(HudRegistry registry) {
        registry.register(new HudRegistry.Component(ID, "Scanner Debug (developer)", this::width, this::height, this::render),
                new HudLayoutManager.WidgetState(HudLayoutManager.Anchor.TOP_LEFT, 8, 120, false));
    }
    private List<String> lines() {
        var usage = scanners.lastUsage();
        var block = modules.getModule("BlockESP");
        var storage = modules.getModule("StorageESP");
        int blockCount = block instanceof BlockESP esp ? esp.getMatches().size() : 0;
        int storageCount = storage instanceof StorageESP esp ? esp.getCachedPositions().size() : 0;
        return List.of("SCANNER DEBUG / last client tick",
                "Block probes: " + usage.blocks() + " / " + ScannerService.BLOCK_BUDGET,
                "Chunk lookups: " + usage.chunkLookups() + " / " + ScannerService.CHUNK_BUDGET,
                "Entity checks: " + usage.entities() + " / " + ScannerService.ENTITY_BUDGET,
                "Task steps: " + usage.steps() + " / 16384",
                "Markers: block " + blockCount + " / storage " + storageCount,
                String.format(Locale.ROOT, "Discovery elapsed: %.3f ms", scanners.lastElapsedNanos() / 1_000_000.0));
    }
    private int width() { return lines().stream().mapToInt(Minecraft.getInstance().font::width).max().orElse(220) + 12; }
    private int height() { return 7 * (Minecraft.getInstance().font.lineHeight + 2) + 10; }
    private void render(ClientEvents.HudRender event) {
        var font = Minecraft.getInstance().font;
        var graphics = event.graphics();
        var lines = lines();
        int width = lines.stream().mapToInt(font::width).max().orElse(220) + 12, height = height();
        int x = layout.resolveX(ID, graphics.guiWidth(), width), y = layout.resolveY(ID, graphics.guiHeight(), height);
        graphics.fill(x, y, x + width, y + height, ClientUiTheme.PANEL);
        for (int index = 0; index < lines.size(); index++) {
            graphics.text(font, lines.get(index), x + 6, y + 5 + index * (font.lineHeight + 2),
                    index == 0 ? ClientUiTheme.ACCENT : ClientUiTheme.TEXT, true);
        }
    }
}
