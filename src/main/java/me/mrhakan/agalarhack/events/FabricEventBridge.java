package me.mrhakan.agalarhack.events;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.resources.Identifier;

/** The single Fabric-to-client bridge. Screen input subscriptions follow Fabric screen lifetimes. */
public final class FabricEventBridge {
    private FabricEventBridge() { }
    public static void register(EventBus bus) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> bus.post(new ClientEvents.ClientTick(client)));
        ClientTickEvents.END_LEVEL_TICK.register(level -> bus.post(new ClientEvents.WorldTick(level)));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> bus.post(new ClientEvents.Connected(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> bus.post(new ClientEvents.Disconnected(client)));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_LOAD.register(
                (level, chunk) -> bus.post(new ClientEvents.ChunkLoaded(level, chunk)));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_UNLOAD.register(
                (level, chunk) -> bus.post(new ClientEvents.ChunkUnloaded(level, chunk)));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register(
                (entity, level) -> bus.post(new ClientEvents.BlockEntityLoaded(level, entity)));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register(
                (entity, level) -> bus.post(new ClientEvents.BlockEntityUnloaded(level, entity)));
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> bus.post(new ClientEvents.EntityAdded(entity, level)));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> bus.post(new ClientEvents.EntityRemoved(entity, level)));
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> bus.post(new ClientEvents.RenderSubmit(context)));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("agalarhack", "hud"),
                (graphics, delta) -> bus.post(new ClientEvents.HudRender(graphics, delta)));
        ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
            me.mrhakan.agalarhack.AgalarHackClient.UI_SESSION.transition(screen);
            ScreenKeyboardEvents.beforeKeyPress(screen).register((s, key) -> bus.post(new ClientEvents.ScreenKeyInput(s, key, true)));
            ScreenKeyboardEvents.beforeKeyRelease(screen).register((s, key) -> bus.post(new ClientEvents.ScreenKeyInput(s, key, false)));
            ScreenMouseEvents.beforeMouseClick(screen).register((s, mouse) -> bus.post(new ClientEvents.ScreenMouseInput(s, mouse, true)));
            ScreenMouseEvents.beforeMouseRelease(screen).register((s, mouse) -> bus.post(new ClientEvents.ScreenMouseInput(s, mouse, false)));
        });
        // Screen identity is observed at tick boundaries; resize/re-init is not an open event.
    }
}
