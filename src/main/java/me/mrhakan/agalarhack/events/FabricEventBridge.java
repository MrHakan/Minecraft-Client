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
        // Chat observation and local-only filtering; Fabric has no hook for rewriting a chat line,
        // so nothing here modifies a message.
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_CHAT.register(
                (message, signed, sender, params, timestamp) -> bus.postAllowed(new ClientEvents.ChatReceived(message.getString(), false)));
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_GAME.register(
                (message, overlay) -> overlay || bus.postAllowed(new ClientEvents.ChatReceived(message.getString(), true)));
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> bus.post(new ClientEvents.EntityAdded(entity, level)));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> bus.post(new ClientEvents.EntityRemoved(entity, level)));
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> bus.post(new ClientEvents.RenderSubmit(context)));
        // Every HUD widget is scaled here rather than each one scaling itself: one push and pop
        // around the whole overlay, with the layout working in the matching logical size so an
        // anchored widget still reaches its edge. See HudScale.
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("agalarhack", "hud"), (graphics, delta) -> {
            float scale = (float) me.mrhakan.agalarhack.AgalarHackClient.HUD_LAYOUT.scale();
            if (scale == 1.0f) {
                bus.post(new ClientEvents.HudRender(graphics, delta));
                return;
            }
            graphics.pose().pushMatrix();
            graphics.pose().scale(scale, scale);
            try { bus.post(new ClientEvents.HudRender(graphics, delta)); }
            finally { graphics.pose().popMatrix(); }
        });
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
