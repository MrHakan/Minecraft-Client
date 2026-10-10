package me.mrhakan.agalarhack.gametest;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.InventoryService;
import me.mrhakan.agalarhack.services.PacketRates;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives the client against a real dedicated server over a real connection.
 *
 * <p>Every other scenario in this suite runs single-player against the integrated server, which is
 * honest evidence about the client and no evidence at all about two things: the packet counters,
 * whose mixins were verified *applied* to the bytecode but had never been observed *firing*, and
 * what container automation does when a click has to cross a socket and come back. Fabric's client
 * game test API can start a dedicated server and connect to it, so neither of those has to stay a
 * manual check.
 *
 * <p>Waits are on observable state rather than on the clock. The one exception is the packet rate,
 * which is a per-second average and therefore cannot resolve faster than the window it averages
 * over; even there the wait is for samples to exist, not for a fixed number of ticks.
 *
 * <p><strong>Off unless the server EULA is accepted.</strong> A dedicated server refuses to start
 * until {@code eula.txt} says so, and the harness recreates its run directory on every run, so
 * accepting it means automation writing that file. Agreeing to Mojang's EULA
 * (https://aka.ms/MinecraftEULA) is the repository owner's decision and not a test's, so this
 * scenario does nothing unless {@code -PacceptServerEula=true} was passed - or
 * {@code AGALARHACK_ACCEPT_SERVER_EULA=true} in the environment, which {@code tools/smoke-client.sh}
 * turns into that flag. When it is off this logs a warning and returns, and it must not be counted
 * as coverage: nothing here has run.
 *
 * <p><strong>What this is not.</strong> A dedicated server on loopback is not a busy public server:
 * there is one player, no latency to speak of and no other traffic. It proves the code paths a
 * remote connection uses, not behaviour under adversarial network conditions.
 */
public final class DedicatedServerGameTest implements FabricClientGameTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("agalarhack-gametest");

    /** Generous: a rate needs more than a second of real time before it can be non-zero. */
    private static final int RATE_TIMEOUT_TICKS = 400;

    /** Set by the build only when the acceptance flag was passed. */
    private static final String EULA_PROPERTY = "agalarhack.acceptServerEula";

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!Boolean.getBoolean(EULA_PROPERTY)) {
            LOGGER.warn("  SKIPPED: dedicated-server scenarios need Minecraft's server EULA "
                    + "accepted. Nothing below this ran, so it is not evidence of anything. Pass "
                    + "-PacceptServerEula=true (or AGALARHACK_ACCEPT_SERVER_EULA=true to "
                    + "tools/smoke-client.sh) to agree to https://aka.ms/MinecraftEULA and run it.");
            return;
        }
        acceptTheServerEula(context);
        try (TestDedicatedServerContext server = context.worldBuilder()
                .setUseConsistentSettings(true).createServer()) {
            try (var connection = server.connect()) {
                connection.waitForChunksRender();
                context.runOnClient(client -> AgalarHackClient.moduleManager.getModuleList()
                        .forEach(module -> module.setToggled(false, false)));
                aRealRemoteConnection(context);
                theNettyCountersFire(context);
                equipsOverTheWire(context, server, connection);
                equipsOverALaggyConnection(context, server, connection);
            }
            disconnectClearedTheCounters(context);
            reconnectingWorksAgain(context, server);
        } finally {
            context.runOnClient(client -> {
                var serverInfo = AgalarHackClient.moduleManager.getModule("ServerInfo");
                if (serverInfo != null) {
                    serverInfo.setToggled(false, false);
                    serverInfo.settings.setSetting("packetRates", false);
                }
                var autoArmor = AgalarHackClient.moduleManager.getModule("AutoArmor");
                if (autoArmor != null) autoArmor.setToggled(false, false);
            });
        }
    }

    /**
     * The control for everything below: this has to be a remote connection, not the integrated one.
     *
     * <p>If the client were still single-player here, the counter and round-trip claims would be
     * about the local channel and would duplicate coverage the suite already has.
     */
    private void aRealRemoteConnection(ClientGameTestContext context) {
        boolean remote = context.computeOnClient(client -> client.getConnection() != null
                && !client.hasSingleplayerServer() && client.getConnection().getConnection() != null);
        if (!remote) {
            throw new AssertionError("the client is not on a remote connection, so this scenario is "
                    + "testing the integrated server and proves nothing new");
        }
    }

    /**
     * First observation of the two {@code Connection} mixins actually counting.
     *
     * <p>Driven through the product path: ServerInfo's own HUD line is what asks for counting, and
     * asking is what keeps it alive, so the line is called every tick exactly as a visible widget
     * would call it. That also means the assertion covers the formatting a player sees, not just the
     * service behind it.
     */
    private void theNettyCountersFire(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var module = AgalarHackClient.moduleManager.getModule("ServerInfo");
            module.settings.setSetting("packetRates", true);
            module.setToggled(true, false);
        });
        context.waitFor(client -> {
            // Calling the line is the request to keep counting; a widget on screen does the same.
            serverInfoLine(client);
            var rates = ClientServices.require(PacketRates.class);
            return rates.hasSamples() && rates.inboundPerSecond() > 0 && rates.outboundPerSecond() > 0;
        }, RATE_TIMEOUT_TICKS);
        String line = context.computeOnClient(DedicatedServerGameTest::serverInfoLine);
        if (line == null || !line.startsWith("Packets ") || line.contains("...")) {
            throw new AssertionError("the packet line reads \"" + line + "\" after counting started");
        }
        LOGGER.info("    real connection: {}", line);
    }

    private static String serverInfoLine(net.minecraft.client.Minecraft client) {
        var module = AgalarHackClient.moduleManager.getModule("ServerInfo");
        return module instanceof me.mrhakan.agalarhack.module.misc.ServerInfo info
                ? info.packetRateLine() : null;
    }

    /**
     * A container plan whose every click is a packet the server has to accept and echo.
     *
     * <p>Asserted on the server's own copy of the player, because the client predicting an equip it
     * never actually got is precisely the failure a single-player test cannot see.
     */
    private void equipsOverTheWire(ClientGameTestContext context, TestDedicatedServerContext server,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection connection) {
        server.runOnServer(unused -> {
            var player = connection.getServerPlayer();
            player.getInventory().clearContent();
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.getInventory().setItem(14, new ItemStack(Items.DIAMOND_HELMET));
            player.containerMenu.broadcastChanges();
        });
        context.waitFor(client -> client.player.getInventory().getItem(14).is(Items.DIAMOND_HELMET));
        context.runOnClient(client -> {
            require(client.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),
                    "the helmet was already equipped before AutoArmor was switched on");
            AgalarHackClient.moduleManager.getModule("AutoArmor").setToggled(true, false);
        });
        server.waitFor(unused -> connection.getServerPlayer()
                .getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), 200);
        connection.waitForServerboundPackets();
        server.runOnServer(unused -> {
            var player = connection.getServerPlayer();
            require(player.inventoryMenu.getCarried().isEmpty(),
                    "the server still shows a stack on the cursor after the transfer finished");
            int helmets = 0;
            for (int slot = 0; slot < 36; slot++) {
                if (player.getInventory().getItem(slot).is(Items.DIAMOND_HELMET)) helmets++;
            }
            require(helmets == 0, "a helmet is both equipped and in the inventory: " + helmets);
        });
        context.waitFor(client -> client.player.inventoryMenu.getCarried().isEmpty()
                && !ClientServices.require(InventoryService.class).transfers().busy(), 100);
        LOGGER.info("    AutoArmor equipped across a real connection and the server agrees");
    }

    /** Added to every packet the client sends while {@link #equipsOverALaggyConnection} runs. */
    private static final long LAG_MILLIS = 150;

    /**
     * The same plan with every packet the client sends held back {@value #LAG_MILLIS} ms.
     *
     * <p>Loopback has no latency to speak of, so the plan above never had a click in flight while
     * the next one was due. Here every click arrives late and its confirmation later still, which
     * is where a transfer that trusts its own prediction, or gives up waiting, would duplicate or
     * lose the item. The control is the same equip on the plain connection just above, timed the
     * same way, and the handler counts what it held back, which is the evidence the delay reached
     * the connection. It sits at the head of the client's own pipeline, so it holds back the encoded
     * bytes and keeps their order.
     */
    private void equipsOverALaggyConnection(ClientGameTestContext context, TestDedicatedServerContext server,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection connection) {
        long plain = timedEquip(context, server, connection, Items.GOLDEN_HELMET, 20);
        io.netty.channel.Channel channel = context.computeOnClient(client -> clientChannel(client));
        String name = "agalarhack-test-lag";
        java.util.concurrent.atomic.AtomicInteger delayed = new java.util.concurrent.atomic.AtomicInteger();
        channel.pipeline().addFirst(name, new io.netty.channel.ChannelOutboundHandlerAdapter() {
            @Override
            public void write(io.netty.channel.ChannelHandlerContext ctx, Object message, io.netty.channel.ChannelPromise promise) {
                delayed.incrementAndGet();
                ctx.executor().schedule(() -> ctx.write(message, promise), LAG_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
            }

            @Override
            public void flush(io.netty.channel.ChannelHandlerContext ctx) {
                ctx.executor().schedule(ctx::flush, LAG_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        });
        try {
            int before = delayed.get();
            long lagged = timedEquip(context, server, connection, Items.CHAINMAIL_HELMET, 21);
            int held = delayed.get() - before;
            LOGGER.info("    AutoArmor equipped with {} ms added to every client packet ({} packets held back): {} ms "
                    + "against {} ms without, nothing duplicated or lost", LAG_MILLIS, held, lagged, plain);
            // The control: the equip's own packets went through the delay. Timing alone is too coarse
            // for this, since the server is polled once a tick.
            if (held == 0) {
                throw new AssertionError("no client packet passed through the delay while the equip ran, so the "
                        + "scenario proves nothing about latency");
            }
        } finally {
            if (channel.pipeline().get(name) != null) channel.pipeline().remove(name);
        }
    }

    /**
     * Swaps in {@code helmet} through AutoArmor and waits for the server to agree, then checks that
     * exactly one of each helmet exists and both cursors are empty.
     *
     * @return milliseconds from switching AutoArmor on to the server seeing the helmet equipped
     */
    private long timedEquip(ClientGameTestContext context, TestDedicatedServerContext server,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection connection,
            net.minecraft.world.item.Item helmet, int slot) {
        context.runOnClient(client -> AgalarHackClient.moduleManager.getModule("AutoArmor").setToggled(false, false));
        context.waitFor(client -> !ClientServices.require(InventoryService.class).transfers().busy(), 100);
        server.runOnServer(unused -> {
            var player = connection.getServerPlayer();
            player.getInventory().clearContent();
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.getInventory().setItem(slot, new ItemStack(helmet));
            player.containerMenu.broadcastChanges();
        });
        context.waitFor(client -> client.player.getInventory().getItem(slot).is(helmet)
                && client.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), 200);
        long started = System.nanoTime();
        context.runOnClient(client -> AgalarHackClient.moduleManager.getModule("AutoArmor").setToggled(true, false));
        server.waitFor(unused -> connection.getServerPlayer().getItemBySlot(EquipmentSlot.HEAD).is(helmet), 400);
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        context.waitFor(client -> client.player.inventoryMenu.getCarried().isEmpty()
                && !ClientServices.require(InventoryService.class).transfers().busy(), 200);
        connection.waitForServerboundPackets();
        server.runOnServer(unused -> {
            var player = connection.getServerPlayer();
            require(player.inventoryMenu.getCarried().isEmpty(),
                    "the server still shows a stack on the cursor after the transfer finished");
            int carried = 0;
            for (int index = 0; index < 36; index++) if (player.getInventory().getItem(index).is(helmet)) carried++;
            require(carried == 0, "a " + helmet + " is both equipped and in the inventory: " + carried);
        });
        return elapsed;
    }

    /** The client connection's Netty channel; private in {@code Connection}, and these tests run on named mappings. */
    private static io.netty.channel.Channel clientChannel(net.minecraft.client.Minecraft client) {
        try {
            var field = net.minecraft.network.Connection.class.getDeclaredField("channel");
            field.setAccessible(true);
            return (io.netty.channel.Channel) field.get(client.getConnection().getConnection());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("could not reach the client's Netty channel", failure);
        }
    }

    /** A new connection counts from zero, so leaving one has to clear what the last one measured. */
    private void disconnectClearedTheCounters(ClientGameTestContext context) {
        context.waitFor(client -> {
            var rates = ClientServices.require(PacketRates.class);
            return !rates.isCounting() && !rates.hasSamples();
        }, 100);
        boolean channelFree = context.computeOnClient(client ->
                !ClientServices.require(InventoryService.class).transfers().busy());
        if (!channelFree) {
            throw new AssertionError("the container channel is still owned after a disconnect, so a "
                    + "reconnect would start with the previous connection's transfer in progress");
        }
    }

    /** The second connection is the one that would expose state kept from the first. */
    private void reconnectingWorksAgain(ClientGameTestContext context, TestDedicatedServerContext server) {
        try (var connection = server.connect()) {
            connection.waitForChunksRender();
            server.runOnServer(unused -> {
                var player = connection.getServerPlayer();
                player.getInventory().clearContent();
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.getInventory().setItem(9, new ItemStack(Items.IRON_HELMET));
                player.containerMenu.broadcastChanges();
            });
            context.waitFor(client -> client.player.getInventory().getItem(9).is(Items.IRON_HELMET));
            server.waitFor(unused -> connection.getServerPlayer()
                    .getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), 200);
            LOGGER.info("    reconnected and equipped again, so nothing from the first connection "
                    + "blocked the second");
        }
    }

    /**
     * Writes the acceptance the dedicated server demands, having been told to.
     *
     * <p>Done here rather than in a committed file because the harness recreates the run directory
     * for every run, so anything checked in would be deleted before the server looked for it.
     */
    private void acceptTheServerEula(ClientGameTestContext context) {
        java.nio.file.Path eula = context.computeOnClient(client ->
                client.gameDirectory.toPath().resolve("eula.txt"));
        try {
            java.nio.file.Files.writeString(eula, "# Written by DedicatedServerGameTest because "
                    + "-PacceptServerEula=true was passed.\neula=true\n");
        } catch (java.io.IOException failure) {
            throw new AssertionError("could not write " + eula + ", so the dedicated server will "
                    + "refuse to start", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
