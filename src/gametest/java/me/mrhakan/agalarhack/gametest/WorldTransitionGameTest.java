package me.mrhakan.agalarhack.gametest;

import java.util.ArrayList;
import java.util.List;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.events.EventBus;
import me.mrhakan.agalarhack.managers.CommandManager;
import me.mrhakan.agalarhack.module.render.Freecam;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.InventoryService;
import me.mrhakan.agalarhack.services.LookController;
import me.mrhakan.agalarhack.services.ServerContextService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.slf4j.LoggerFactory;

/** Travels to a server-owned dimension and closes the actual connection; never posts lifecycle events. */
public final class WorldTransitionGameTest implements FabricClientGameTest {
    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private ClientLevel oldLevel;
    private Entity oldCamera;
    private boolean dimensionBefore, dimensionAfter, disconnectBefore, disconnectAfter;

    @Override public void runTest(ClientGameTestContext context) {
        try {
            try (var singleplayer = context.worldBuilder().setUseConsistentSettings(true).create()) {
                singleplayer.getConnection().waitForChunksRender();
                context.runOnClient(client -> {
                    AgalarHackClient.moduleManager.getModuleList().forEach(module -> module.setToggled(false, false));
                    oldLevel = client.level;
                    arm(client);
                    oldCamera = client.getCameraEntity();
                    require(oldCamera != client.player, "Freecam control never detached");
                    subscribe();
                });
                // Verified against Fabric API 26.2 TestServerContext.runCommand. Vanilla performs
                // the teleport, sends the dimension packets and replaces the client's level/player.
                singleplayer.getServer().runCommand("execute in minecraft:the_nether run tp @a 0 130 0");
                context.waitFor(client -> client.level != null && client.level != oldLevel
                        && client.level.dimension().equals(Level.NETHER), 600);
                context.waitTicks(3);
                context.runOnClient(client -> {
                    require(dimensionBefore && dimensionAfter, "No complete real dimension-transition observation");
                    require(failures.isEmpty(), String.join("; ", failures));
                    var freecam = (Freecam) AgalarHackClient.moduleManager.getModule("Freecam");
                    require(freecam.isToggled(), "Freecam failed during world replacement");
                    require(client.getCameraEntity() != oldCamera && client.getCameraEntity().level() == client.level,
                            "Freecam retained an old-world camera");
                    freecam.setToggled(false, false);
                    require(client.getCameraEntity() == client.player, "Freecam did not restore the replacement player");
                    LoggerFactory.getLogger("agalarhack-gametest").info(
                            "Dimension transition passed: real Nether level, held look cancelled and Freecam restored to replacement player");
                    // A fresh held command and detached camera must also survive a real disconnect
                    // correctly. Keep Freecam enabled until close() drives Fabric's disconnect hook.
                    arm(client);
                });
            }
            context.runOnClient(client -> {
                require(disconnectBefore && disconnectAfter, "No complete real disconnect observation");
                require(failures.isEmpty(), String.join("; ", failures));
                require(!ClientServices.require(LookController.class).active(), "Held look survived disconnect");
                require(!ClientServices.require(ServerContextService.class).ready(), "Server context still ready after disconnect");
                require(((Freecam) AgalarHackClient.moduleManager.getModule("Freecam")).getBodyAnchor() == null,
                        "Freecam body anchor survived disconnect");
                LoggerFactory.getLogger("agalarhack-gametest").info(
                        "Disconnect transition passed: active look, camera/body state and server readiness cleared");
            });
        } finally {
            context.runOnClient(client -> {
                subscriptions.forEach(EventBus.Subscription::close);
                subscriptions.clear();
                ClientServices.require(LookController.class).cancel();
                AgalarHackClient.moduleManager.getModule("Freecam").setToggled(false, false);
            });
        }
    }

    private void arm(Minecraft client) {
        client.player.setYRot(0);
        client.player.setXRot(0);
        require(CommandManager.handleChat(".look 170 0"), "Look command was not intercepted");
        var look = ClientServices.require(LookController.class);
        require(look.active(), "Look command did not arm the controller");
        // Keep the SAME controller active through chunk generation. Normal command expiry/arrival
        // could otherwise pass this test without any lifecycle cleanup. The before-listener below
        // proves the aim is still active immediately before production cleanup runs.
        look.aimAt(new LookController.Aim(170, 0, true, 0.01f, 1, 1), 10000);
        AgalarHackClient.moduleManager.getModule("Freecam").setToggled(true, false);
    }

    private void subscribe() {
        subscriptions.add(AgalarHackClient.EVENTS.subscribe(ClientEvents.WorldChanged.class,
                "transition-test-before", 200, event -> {
                    if (event.previousLevel() != oldLevel || event.level() == null || event.level() == oldLevel) return;
                    dimensionBefore = true;
                    if (!ClientServices.require(LookController.class).active()) failures.add("Look already inactive before world cleanup; control proves nothing");
                }));
        subscriptions.add(AgalarHackClient.EVENTS.subscribe(ClientEvents.WorldChanged.class,
                "transition-test-after", -100, event -> {
                    if (event.previousLevel() != oldLevel || event.level() == null || event.level() == oldLevel) return;
                    dimensionAfter = true;
                    if (ClientServices.require(LookController.class).active()) failures.add("Look not cancelled by world cleanup");
                    if (ClientServices.require(InventoryService.class).transfers().busy()) failures.add("Old transfer survived world cleanup");
                }));
        subscriptions.add(AgalarHackClient.EVENTS.subscribe(ClientEvents.Disconnected.class,
                "disconnect-test-before", 200, event -> {
                    disconnectBefore = true;
                    if (!ClientServices.require(LookController.class).active()) failures.add("Look already inactive before disconnect cleanup; control proves nothing");
                }));
        subscriptions.add(AgalarHackClient.EVENTS.subscribe(ClientEvents.Disconnected.class,
                "disconnect-test-after", -100, event -> {
                    disconnectAfter = true;
                    if (ClientServices.require(LookController.class).active()) failures.add("Look not cancelled by disconnect cleanup");
                }));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
