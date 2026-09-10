package me.mrhakan.agalarhack.services;

import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.events.EventBus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/** Observes identity, not just nullability: respawn and dimension replacement are transitions. */
public final class ServerContextService {
    private final EventBus events;
    private LocalPlayer player;
    private ClientLevel level;
    private Screen screen;
    private boolean alive;
    public ServerContextService(EventBus events) { this.events = events; }
    public void tick(Minecraft mc) {
        boolean ready = mc.player != null && mc.level != null && mc.player.isAlive();
        if (player != mc.player || level != mc.level || alive != ready) {
            var previousPlayer = player; var previousLevel = level;
            player = mc.player; level = mc.level; alive = ready;
            events.post(new ClientEvents.WorldChanged(previousLevel, level, previousPlayer, player, ready));
        }
        Screen next = mc.gui.screen();
        if (screen != next) {
            Screen previous = screen; screen = next;
            if (previous != null) events.post(new ClientEvents.ScreenClosed(previous));
            if (next != null) events.post(new ClientEvents.ScreenOpened(next));
        }
    }
    public void disconnected() { player = null; level = null; alive = false; }
    public boolean ready() { return alive; }
    public String address(Minecraft mc) {
        var server = mc.getCurrentServer();
        return server == null ? (mc.level == null ? "Disconnected" : "Singleplayer") : server.ip;
    }
}
