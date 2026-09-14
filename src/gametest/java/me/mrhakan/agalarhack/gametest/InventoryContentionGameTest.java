package me.mrhakan.agalarhack.gametest;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.InventoryService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What a container plan does when the inventory changes underneath it between clicks.
 *
 * <p>A plan is computed once and then executed one click per tick, and the controller never
 * re-reads the slots it is about to click - it cannot, because its {@code Controls} interface
 * deliberately exposes clicking and one free-slot query and nothing else. On a real server the
 * inventory can change between those clicks for reasons the client neither causes nor sees coming:
 * a plugin, a command, a container the server edits, an item breaking.
 *
 * <p>The claim under test is therefore not "the right item ends up in the right place" - with the
 * state changed mid-plan there is no single right answer, and inventing one would be this test
 * asserting its own opinion. The claim is the one that matters for a player: <strong>nothing is
 * created and nothing is destroyed</strong>, no stack is left on either cursor, and the channel is
 * handed back. Item safety, not item placement.
 *
 * <p>Three item types are used so the accounting cannot pass by coincidence, and the mutation lands
 * in the middle of the plan by holding the click delay wide open.
 */
public final class InventoryContentionGameTest implements FabricClientGameTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("agalarhack-gametest");

    /** Wide enough that the mutation lands between two clicks rather than racing them. */
    private static final double WIDE_CLICK_DELAY = 20.0;
    private static final int SOURCE_SLOT = 10;
    private static final int SPARE_SLOT = 11;
    private static final int DIAMOND_COUNT = 5;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer =
                     context.worldBuilder().setUseConsistentSettings(true).create()) {
            singleplayer.getConnection().waitForChunksRender();
            context.runOnClient(client -> AgalarHackClient.moduleManager.getModuleList()
                    .forEach(module -> module.setToggled(false, false)));
            theMutationItselfLosesNothing(context, singleplayer);
            aPeerChangeMidPlanLosesNothing(context, singleplayer);
        } finally {
            context.runOnClient(client -> {
                var module = AgalarHackClient.moduleManager.getModule("AutoArmor");
                if (module != null) {
                    module.setToggled(false, false);
                    module.settings.setSetting("clickDelay", 1.0);
                }
            });
        }
    }

    private void aPeerChangeMidPlanLosesNothing(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        seed(context, singleplayer);
        context.runOnClient(client -> {
            require(client.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),
                    "the helmet was equipped before AutoArmor ran");
            require(client.player.inventoryMenu.getCarried().isEmpty(), "the cursor started occupied");
            var module = AgalarHackClient.moduleManager.getModule("AutoArmor");
            module.settings.setSetting("clickDelay", WIDE_CLICK_DELAY);
            module.setToggled(true, false);
        });

        // The plan is [source, armour, source]. Waiting for the carried helmet means click one has
        // happened and click two has not, which is the window a real server would change things in.
        context.waitFor(client -> client.player.inventoryMenu.getCarried().is(Items.DIAMOND_HELMET), 200);
        require(context.computeOnClient(client ->
                        ClientServices.require(InventoryService.class).transfers().busy()),
                "the transfer finished before the mutation could land; the click delay is too short");

        mutate(singleplayer);

        context.waitFor(client -> !ClientServices.require(InventoryService.class).transfers().busy()
                && client.player.inventoryMenu.getCarried().isEmpty(), 400);
        context.runOnClient(client ->
                AgalarHackClient.moduleManager.getModule("AutoArmor").setToggled(false, false));
        // Long enough for any server-side menu correction to arrive; a count taken mid-resync would
        // report a loss that is really a screenshot of a state in flight.
        context.waitTicks(40);

        String accounting = accounting(singleplayer);
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            require(player.inventoryMenu.getCarried().isEmpty(),
                    "the server still shows a stack on the cursor: " + accounting);
            require(count(player, Items.DIAMOND_HELMET) == 1
                            && count(player, Items.IRON_HELMET) == 1
                            && count(player, Items.DIAMOND) == DIAMOND_COUNT,
                    "items were created or destroyed by a mid-plan inventory change - " + accounting
                            + " - expected one of each helmet and " + DIAMOND_COUNT + " diamonds");
        });
        context.runOnClient(client -> {
            require(client.player.inventoryMenu.getCarried().isEmpty(),
                    "the client still shows a stack on the cursor");
            require(!ClientServices.require(InventoryService.class).transfers().busy(),
                    "the container channel was never handed back after the mutated plan");
        });
        LOGGER.info("  Inventory contention passed: a mid-plan peer change kept every item and both "
                + "cursors empty ({})", accounting);
    }

    /**
     * The same server-side change, with no transfer in flight and an empty cursor.
     *
     * <p>This is the control, and it exists because the first version of this scenario reported
     * items destroyed and had no way to say who destroyed them. If the change itself does not
     * conserve items, nothing the contention phase observes can be attributed to the client.
     */
    private void theMutationItselfLosesNothing(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        seed(context, singleplayer);
        mutate(singleplayer);
        context.waitTicks(20);
        String accounting = accounting(singleplayer);
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            require(count(player, Items.DIAMOND_HELMET) == 1
                            && count(player, Items.IRON_HELMET) == 1
                            && count(player, Items.DIAMOND) == DIAMOND_COUNT,
                    "the server-side change loses items on its own, with no transfer running and an "
                            + "empty cursor - " + accounting + ". The contention phase below cannot "
                            + "attribute anything to the client until this holds.");
        });
        LOGGER.info("    control: the same change with nothing running conserves every item ({})",
                accounting);
    }

    /** Puts the fixture back to a known state: one diamond helmet in the source slot, head empty. */
    private void seed(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            player.getInventory().clearContent();
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.getInventory().setItem(SOURCE_SLOT, new ItemStack(Items.DIAMOND_HELMET));
            player.containerMenu.broadcastChanges();
        });
        context.waitFor(client -> client.player.getInventory().getItem(SOURCE_SLOT).is(Items.DIAMOND_HELMET));
        context.waitTicks(5);
    }

    /**
     * A change an operator could actually make, through vanilla commands rather than by writing
     * into the inventory behind the menu's back. `/item replace` is the realistic version of "a
     * plugin moved something while your transfer was half done".
     */
    private void mutate(TestSingleplayerContext singleplayer) {
        // Two details this took two runs to get right, both caught by the control rather than by
        // inspection.
        //
        // `@a`, not `@s`: runCommand executes as the server console, which is not an entity, so
        // `@s` resolves to nothing and the change silently does not happen.
        //
        // And both targets are slots that are EMPTY at this moment - the spare slot, and the head
        // slot whose helmet is on the cursor mid-plan. `/item replace` overwrites, destroying
        // whatever occupied the slot, so aiming it at an occupied slot would make this scenario
        // report the command's own destruction as an inventory-safety failure.
        singleplayer.getServer().runCommand(
                "item replace entity @a inventory." + (SPARE_SLOT - 9) + " with minecraft:diamond "
                        + DIAMOND_COUNT);
        singleplayer.getServer().runCommand("item replace entity @a armor.head with minecraft:iron_helmet");
    }

    private String accounting(TestSingleplayerContext singleplayer) {
        return singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            return "diamond helmet=" + count(player, Items.DIAMOND_HELMET)
                    + " iron helmet=" + count(player, Items.IRON_HELMET)
                    + " diamond=" + count(player, Items.DIAMOND)
                    + " serverCursor=" + player.inventoryMenu.getCarried();
        });
    }

    /**
     * Every copy of an item the player holds anywhere.
     *
     * <p>The inventory container is the whole of it: 26.2's {@code Inventory.getContainerSize()} is
     * {@code items.size() + EQUIPMENT_SLOT_MAPPING.size()}, so armour and offhand are already in
     * that range. An earlier version of this method also walked {@code EquipmentSlot.values()} and
     * therefore counted everything worn twice, which read exactly like item duplication.
     */
    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
