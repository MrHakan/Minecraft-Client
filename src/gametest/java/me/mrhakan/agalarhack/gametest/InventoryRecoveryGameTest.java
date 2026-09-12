package me.mrhakan.agalarhack.gametest;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.InventoryService;
import me.mrhakan.agalarhack.ui.ModuleSettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.LoggerFactory;

/** Real menu clicks and a real client settings screen, not a fabricated carried-stack snapshot. */
public final class InventoryRecoveryGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var singleplayer = context.worldBuilder().setUseConsistentSettings(true).create()) {
            singleplayer.getConnection().waitForChunksRender();
            context.runOnClient(client -> AgalarHackClient.moduleManager.getModuleList()
                    .forEach(module -> module.setToggled(false, false)));
            singleplayer.getServer().runOnServer(server -> {
                var player = singleplayer.getConnection().getServerPlayer();
                player.getInventory().clearContent();
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.getInventory().setItem(10, new ItemStack(Items.DIAMOND_HELMET));
                player.containerMenu.broadcastChanges();
            });
            context.waitFor(client -> client.player.getInventory().getItem(10).is(Items.DIAMOND_HELMET));
            context.waitTicks(10);
            context.runOnClient(client -> {
                require(client.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "Helmet equipped with AutoArmor off");
                require(client.player.inventoryMenu.getCarried().isEmpty(), "Control already carried a stack");
                var module = AgalarHackClient.moduleManager.getModule("AutoArmor");
                module.settings.setSetting("clickDelay", 10.0);
                module.setToggled(true, false);
            });
            context.waitFor(client -> client.player.inventoryMenu.getCarried().is(Items.DIAMOND_HELMET));
            context.runOnClient(client -> {
                var module = AgalarHackClient.moduleManager.getModule("AutoArmor");
                client.gui.setScreen(new ModuleSettingsScreen(null, module));
                module.setToggled(false, false);
                var transfers = ClientServices.require(InventoryService.class).transfers();
                require(transfers.owns("autoarmor") && transfers.recovering(),
                        "Disabling through an open settings screen abandoned the carried helmet");
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                require(client.player.inventoryMenu.getCarried().is(Items.DIAMOND_HELMET),
                        "Recovery clicked while a settings screen held the input channel");
                client.gui.setScreen(null);
            });
            context.waitFor(client -> client.player.inventoryMenu.getCarried().isEmpty()
                    && !ClientServices.require(InventoryService.class).transfers().busy(), 100);
            singleplayer.getServer().waitFor(server -> {
                var player = singleplayer.getConnection().getServerPlayer();
                return player.inventoryMenu.getCarried().isEmpty();
            });
            singleplayer.getServer().runOnServer(server -> {
                var player = singleplayer.getConnection().getServerPlayer();
                require(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "Cancelled equip plan resumed after screen closed");
                int helmets = 0;
                for (int slot=0; slot<36; slot++) {
                    var stack = player.getInventory().getItem(slot);
                    if (stack.is(Items.DIAMOND_HELMET)) helmets += stack.getCount();
                }
                require(helmets == 1, "Cancelled transfer lost or duplicated the helmet: " + helmets);
            });
            LoggerFactory.getLogger("agalarhack-gametest").info(
                    "Inventory recovery passed: actual pickup, open-screen disable, empty client/server cursors and one retained helmet");
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                var module = AgalarHackClient.moduleManager.getModule("AutoArmor");
                module.setToggled(false, false);
                module.settings.setSetting("clickDelay", 1.0);
            });
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
