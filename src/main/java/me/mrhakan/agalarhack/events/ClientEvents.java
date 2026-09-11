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
    public record WorldChanged(ClientLevel previousLevel, ClientLevel level,
                               net.minecraft.client.player.LocalPlayer previousPlayer,
                               net.minecraft.client.player.LocalPlayer player, boolean ready) { }
    public record InventoryUpdated(net.minecraft.client.player.LocalPlayer player, int slot,
                                   net.minecraft.world.item.ItemStack previous, net.minecraft.world.item.ItemStack current) { }
    /** Separate from main inventory indices; includes initial snapshots after player replacement. */
    public record EquipmentUpdated(net.minecraft.client.player.LocalPlayer player, net.minecraft.world.entity.EquipmentSlot slot,
                                   net.minecraft.world.item.ItemStack previous, net.minecraft.world.item.ItemStack current) { }
    public record SelectedSlotChanged(int previous, int current) { }
    public record KeyInput(int key, boolean pressed, boolean inScreen) { }
    public record MouseInput(int button, boolean pressed, boolean inScreen) { }
    public record WorldTick(ClientLevel level) { }
    public record RenderSubmit(LevelRenderContext context) { }
    public record HudRender(GuiGraphicsExtractor graphics, DeltaTracker delta) { }
    public record Connected(Minecraft client) { }
    public record Disconnected(Minecraft client) { }
    public record ChunkLoaded(ClientLevel level, net.minecraft.world.level.chunk.LevelChunk chunk) { }
    public record ChunkUnloaded(ClientLevel level, net.minecraft.world.level.chunk.LevelChunk chunk) { }
    /** A server-sent block change, posted after the world already holds the new state. */
    public record BlockUpdated(ClientLevel level, net.minecraft.core.BlockPos pos,
                               net.minecraft.world.level.block.state.BlockState state) { }
    /** A server-sent entity event such as a totem activation or an equipment break. */
    public record EntityEventReceived(ClientLevel level, Entity entity, byte eventId) { }
    /** A block entity became available client-side; Fabric provides this hook directly. */
    public record BlockEntityLoaded(ClientLevel level, net.minecraft.world.level.block.entity.BlockEntity entity) { }
    public record BlockEntityUnloaded(ClientLevel level, net.minecraft.world.level.block.entity.BlockEntity entity) { }
    /** Too many changes arrived at once to report individually; treat the whole chunk as stale. */
    public record ChunkBlocksInvalidated(ClientLevel level, int chunkX, int chunkZ) { }
    public record EntityAdded(Entity entity, ClientLevel level) { }
    public record EntityRemoved(Entity entity, ClientLevel level) { }
    public record ScreenOpened(Screen screen) { }
    public record ScreenClosed(Screen screen) { }
    public record ScreenKeyInput(Screen screen, KeyEvent event, boolean pressed) { }
    public record ScreenMouseInput(Screen screen, MouseButtonEvent event, boolean pressed) { }
}
