package me.mrhakan.agalarhack.events;

import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.Entity;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

/** Event payloads are borrowed for synchronous callbacks; never retain render contexts. */
public final class ClientEvents {
    private ClientEvents() { }
    public record ClientTick(Minecraft client) { }
    public record WorldTick(ClientLevel level) { }
    public record RenderSubmit(LevelRenderContext context) { }
    public record HudRender(GuiGraphicsExtractor graphics, DeltaTracker delta) { }
    public record Connected(Minecraft client) { }
    public record Disconnected(Minecraft client) { }
    public record EntityAdded(Entity entity, ClientLevel level) { }
    public record EntityRemoved(Entity entity, ClientLevel level) { }
    public record ScreenOpened(Screen screen) { }
    public record ScreenClosed(Screen screen) { }
    public record ScreenKeyInput(Screen screen, KeyEvent event, boolean pressed) { }
    public record ScreenMouseInput(Screen screen, MouseButtonEvent event, boolean pressed) { }
}
