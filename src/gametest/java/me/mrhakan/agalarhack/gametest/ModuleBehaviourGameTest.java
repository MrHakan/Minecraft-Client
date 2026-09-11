package me.mrhakan.agalarhack.gametest;

import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

/**
 * Checks that individual modules do the thing they exist to do.
 *
 * <p>{@link ModuleLifecycleGameTest} proves a module runs. It cannot tell a module that works from
 * one whose entire body is behind a condition that is never true, because both tick quietly for
 * twenty ticks. This is where a module earns its {@code markExperimental()} flag being cleared: one
 * scenario per module, asserting the effect a player would actually notice.
 *
 * <p>Every scenario asserts the effect is <em>absent</em> before enabling the module. Without that,
 * an assertion that passes because the game does it anyway looks exactly like one the module earned.
 */
public class ModuleBehaviourGameTest implements FabricClientGameTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("agalarhack-gametest");

    /** Generous: an inventory transfer is several server round trips, each with its own delay. */
    private static final int SETTLE_TICKS = 80;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            singleplayer.getConnection().waitForChunksRender();
            singleplayer.getServer().runOnServer(server ->
                    TestScene.build(singleplayer.getConnection().getServerPlayer()));
            context.waitTicks(20);

            autoTotem(context);
            autoArmor(context);

            LOGGER.info("Module behaviour scenarios passed");
        }
    }

    /** A totem in the inventory and nothing in the offhand should end with the totem in the offhand. */
    private void autoTotem(ClientGameTestContext context) {
        Predicate<Minecraft> holdingTotem =
                client -> client.player.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.TOTEM_OF_UNDYING);

        // "always" rather than the health default, because a test player at full health is exactly
        // the case the default is written to skip.
        configure(context, "AutoTotem", module -> module.settings.setSetting("mode", "always"));
        assertNotYet(context, holdingTotem, "the offhand already held a totem before AutoTotem ran");

        toggle(context, "AutoTotem", true);
        boolean equipped = settle(context, holdingTotem);
        toggle(context, "AutoTotem", false);
        if (!equipped) {
            throw new AssertionError("AutoTotem did not move a totem into the offhand within "
                    + SETTLE_TICKS + " ticks, with two in the inventory and the slot empty");
        }
        LOGGER.info("  AutoTotem put a totem in the offhand");
    }

    /** A full diamond set in the inventory and a bare player should end with the set worn. */
    private void autoArmor(ClientGameTestContext context) {
        Predicate<Minecraft> fullyArmoured = client -> {
            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                if (client.player.getItemBySlot(slot).isEmpty()) return false;
            }
            return true;
        };

        assertNotYet(context, fullyArmoured, "the player was already wearing armour before AutoArmor ran");

        toggle(context, "AutoArmor", true);
        // Four pieces, each its own transfer with its own delay between swaps.
        boolean worn = settle(context, fullyArmoured, SETTLE_TICKS * 2);
        toggle(context, "AutoArmor", false);
        if (!worn) {
            throw new AssertionError("AutoArmor did not equip all four armour slots within "
                    + (SETTLE_TICKS * 2) + " ticks, with a full diamond set in the inventory");
        }
        LOGGER.info("  AutoArmor equipped a full set");
    }

    private static void configure(ClientGameTestContext context, String name, java.util.function.Consumer<Module> change) {
        context.runOnClient(client -> change.accept(AgalarHackClient.moduleManager.getModule(name)));
    }

    private static void toggle(ClientGameTestContext context, String name, boolean on) {
        context.runOnClient(client -> AgalarHackClient.moduleManager.getModule(name).setToggled(on, false));
    }

    /**
     * The control half of every scenario. A behaviour assertion that was already true before the
     * module was switched on measures the game, not the mod.
     */
    private static void assertNotYet(ClientGameTestContext context, Predicate<Minecraft> effect, String complaint) {
        if (context.computeOnClient(effect::test)) {
            throw new AssertionError(complaint + "; the scenario proves nothing in that state");
        }
    }

    private static boolean settle(ClientGameTestContext context, Predicate<Minecraft> effect) {
        return settle(context, effect, SETTLE_TICKS);
    }

    /** Polls rather than waiting the full budget, so a working module costs a few ticks, not eighty. */
    private static boolean settle(ClientGameTestContext context, Predicate<Minecraft> effect, int budget) {
        for (int waited = 0; waited < budget; waited += 4) {
            context.waitTicks(4);
            if (context.computeOnClient(effect::test)) return true;
        }
        return false;
    }
}
