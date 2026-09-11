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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

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
            cameraTweaks(context);
            autoWalk(context, singleplayer);
            safeWalk(context, singleplayer);
            autoRefill(context, singleplayer);
            inventoryCleaner(context, singleplayer);

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

    /**
     * A borrowed vanilla option must come back. The restore path is the half that breaks quietly:
     * nobody notices a field of view that was never restored until the next session looks wrong.
     */
    private void cameraTweaks(ClientGameTestContext context) {
        int original = context.computeOnClient(client -> client.options.fov().get());
        int forced = original == 45 ? 70 : 45;

        configure(context, "CameraTweaks", module -> {
            module.settings.setSetting("overrideFov", true);
            module.settings.setSetting("fov", (double) forced);
        });

        toggle(context, "CameraTweaks", true);
        boolean applied = settle(context, client -> client.options.fov().get() == forced);
        toggle(context, "CameraTweaks", false);
        if (!applied) {
            throw new AssertionError("CameraTweaks did not force the field of view to " + forced);
        }

        context.waitTicks(4);
        int after = context.computeOnClient(client -> client.options.fov().get());
        if (after != original) {
            throw new AssertionError("CameraTweaks left the field of view at " + after
                    + " after being disabled; it was " + original + " before");
        }
        LOGGER.info("  CameraTweaks forced the field of view and gave it back");
    }

    /** The module holds the movement key; the assertion is that the player actually goes somewhere. */
    private void autoWalk(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        settleOnGround(context, singleplayer);

        Vec3 before = position(context);
        context.waitTicks(20);
        if (position(context).distanceTo(before) > 0.5) {
            throw new AssertionError("the player was already moving before AutoWalk was enabled");
        }

        toggle(context, "AutoWalk", true);
        Vec3 start = position(context);
        boolean moved = settle(context, client -> client.player.position().distanceTo(start) > 2.0);
        toggle(context, "AutoWalk", false);
        if (!moved) {
            throw new AssertionError("AutoWalk did not move the player more than 2 blocks in "
                    + SETTLE_TICKS + " ticks");
        }
        LOGGER.info("  AutoWalk walked the player forward");
    }

    /**
     * The one scenario here that proves a mixin rather than a module. It runs the same walk twice -
     * once with the module off, once on - because "the player did not fall" means nothing unless the
     * same walk without the module does.
     */
    private void safeWalk(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        BlockPos ledge = digPit(context, singleplayer);

        toggle(context, "SafeWalk", false);
        toggle(context, "Parkour", false);
        Walk control = walkOffAndReport(context, singleplayer, ledge);
        if (control.drop() > -1.0) {
            throw new AssertionError("the control walk did not fall off the ledge (dropped only "
                    + control.drop() + " blocks), so neither scenario below can tell a module apart "
                    + "from nothing");
        }
        // A drop deeper than the pit means the player went through its floor and is falling through
        // the world, which is a broken scene rather than a walk off a ledge. Left unchecked that
        // reads as a healthy control and quietly invalidates both scenarios below.
        if (control.drop() < -(PIT_DEPTH + 2.0)) {
            throw new AssertionError("the control walk fell " + control.drop() + " blocks into a pit "
                    + PIT_DEPTH + " deep; the scene is broken, not the module");
        }

        toggle(context, "SafeWalk", true);
        Walk held = walkOffAndReport(context, singleplayer, ledge);
        toggle(context, "SafeWalk", false);
        if (held.drop() < -0.5) {
            throw new AssertionError("SafeWalk let the player drop " + held.drop()
                    + " blocks off a ledge the control walk also fell from");
        }
        LOGGER.info("  SafeWalk held the edge the control walk fell off");

        parkour(context, singleplayer, ledge, control);
    }

    /**
     * Parkour jumps at the edge rather than holding it, so the tell is upward movement: the control
     * walk off the same ledge never rises at all.
     */
    private void parkour(ClientGameTestContext context, TestSingleplayerContext singleplayer,
            BlockPos ledge, Walk control) {
        if (control.rise() > 0.2) {
            throw new AssertionError("the control walk rose " + control.rise()
                    + " blocks without Parkour, so a jump cannot be attributed to the module");
        }

        toggle(context, "Parkour", true);
        Walk jumped = walkOffAndReport(context, singleplayer, ledge);
        toggle(context, "Parkour", false);
        if (jumped.rise() < 0.3) {
            throw new AssertionError("Parkour did not jump at the edge; the player rose only "
                    + jumped.rise() + " blocks walking into a gap");
        }
        LOGGER.info("  Parkour jumped at the edge the control walk stepped off");
    }

    /** How deep the pit is. Deep enough that falling in is unmistakable, shallow enough to survive. */
    private static final int PIT_DEPTH = 3;

    /**
     * Clears a 5x5 pit two blocks away and returns the standing spot facing it.
     *
     * <p>The pit gets a floor. A superflat world is four blocks thick, so digging even this far
     * without one punches straight through the bedrock and the "pit" becomes the void - which is
     * exactly what the first version did. It passed locally, because the player was still falling
     * when the sampling window closed, and failed on a slower CI runner where they fell far enough
     * to die. A test whose meaning depends on how fast the machine is has no meaning.
     */
    private static BlockPos digPit(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        return singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            ServerLevel level = player.level();
            BlockPos stand = player.blockPosition();
            for (int dx = 2; dx <= 6; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlockAndUpdate(stand.offset(dx, -PIT_DEPTH - 1, dz), Blocks.STONE.defaultBlockState());
                    for (int dy = -1; dy >= -PIT_DEPTH; dy--) {
                        level.setBlockAndUpdate(stand.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                    }
                }
            }
            return stand;
        });
    }

    /** How the player's height changed during one walk at the pit, relative to where they started. */
    private record Walk(double drop, double rise) { }

    /**
     * Puts the player back on the ledge and walks them into the pit.
     *
     * <p>The height is sampled throughout rather than only at the end, because a jump and a fall are
     * told apart by what happened in between: a player who jumps the gap and one who never moved
     * both finish level with where they started.
     */
    private static Walk walkOffAndReport(ClientGameTestContext context, TestSingleplayerContext singleplayer,
            BlockPos ledge) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(ledge.getX() + 0.5, ledge.getY(), ledge.getZ() + 0.5);
            // Each walk is its own experiment: a player carrying damage or momentum from the last
            // one is a different player, and three walks in a row would eventually kill them.
            player.setHealth(player.getMaxHealth());
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
        });
        context.waitTicks(20);
        context.getInput().lookAt(ledge.offset(4, 0, 0));
        context.waitTicks(5);

        double startY = position(context).y;
        double lowest = startY;
        double highest = startY;
        context.getInput().holdKey(options -> options.keyUp);
        for (int tick = 0; tick < 50; tick += 2) {
            context.waitTicks(2);
            double y = position(context).y;
            lowest = Math.min(lowest, y);
            highest = Math.max(highest, y);
        }
        context.getInput().releaseKey(options -> options.keyUp);
        context.waitTicks(10);

        singleplayer.getServer().runOnServer(server ->
                singleplayer.getConnection().getServerPlayer().setGameMode(GameType.CREATIVE));
        return new Walk(lowest - startY, highest - startY);
    }

    /** Creative flight leaves the player drifting; several modules only act with both feet down. */
    private static void settleOnGround(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server ->
                singleplayer.getConnection().getServerPlayer().setGameMode(GameType.CREATIVE));
        context.waitTicks(10);
    }

    private static Vec3 position(ClientGameTestContext context) {
        return context.computeOnClient(client -> client.player.position());
    }

    /** A low hotbar stack with a full one behind it in the inventory should be topped up. */
    private void autoRefill(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        final int hotbarSlot = 0;
        final int storageSlot = 20;
        setInventory(singleplayer, slots -> {
            slots.setItem(hotbarSlot, new ItemStack(Items.COBBLESTONE, 4));
            slots.setItem(storageSlot, new ItemStack(Items.COBBLESTONE, 64));
        });
        context.waitTicks(10);

        Predicate<Minecraft> toppedUp =
                client -> client.player.getInventory().getItem(hotbarSlot).getCount() > 4;
        assertNotYet(context, toppedUp, "the hotbar stack was already above the refill threshold");

        toggle(context, "AutoRefill", true);
        boolean refilled = settle(context, toppedUp, SETTLE_TICKS * 2);
        toggle(context, "AutoRefill", false);
        if (!refilled) {
            throw new AssertionError("AutoRefill left a 4-item hotbar stack alone with a full stack "
                    + "of the same item in the inventory");
        }
        LOGGER.info("  AutoRefill topped up a low hotbar stack");
    }

    /**
     * The one automation here that destroys property, so the scenario cares as much about what
     * survives as about what goes. An unlisted item in the next slot must still be there afterwards.
     */
    private void inventoryCleaner(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        final int junkSlot = 20;
        final int keepSlot = 21;
        setInventory(singleplayer, slots -> {
            slots.setItem(junkSlot, new ItemStack(Items.ROTTEN_FLESH, 8));
            slots.setItem(keepSlot, new ItemStack(Items.DIAMOND, 8));
        });
        context.waitTicks(10);

        Predicate<Minecraft> junkGone =
                client -> !client.player.getInventory().getItem(junkSlot).is(Items.ROTTEN_FLESH);
        assertNotYet(context, junkGone, "the junk slot did not contain the junk the scenario placed");

        toggle(context, "InventoryCleaner", true);
        boolean dropped = settle(context, junkGone, SETTLE_TICKS * 2);
        toggle(context, "InventoryCleaner", false);
        if (!dropped) {
            throw new AssertionError("InventoryCleaner did not drop rotten flesh, which is in its "
                    + "default junk list");
        }

        boolean kept = context.computeOnClient(client ->
                client.player.getInventory().getItem(keepSlot).is(Items.DIAMOND));
        if (!kept) {
            throw new AssertionError("InventoryCleaner dropped a diamond, which is not in any junk "
                    + "list; this module must never remove something nobody listed");
        }
        LOGGER.info("  InventoryCleaner dropped the listed junk and left the diamonds alone");
    }

    /**
     * Replaces the player's inventory so a scenario starts from exactly what it placed. The earlier
     * scenarios equip and swap things, so whatever is left by then is not a foundation to assert on.
     */
    private static void setInventory(TestSingleplayerContext singleplayer,
            java.util.function.Consumer<net.minecraft.world.entity.player.Inventory> change) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            player.getInventory().clearContent();
            change.accept(player.getInventory());
            // Without this the client keeps drawing - and the modules keep reading - the old contents.
            player.containerMenu.broadcastChanges();
        });
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
