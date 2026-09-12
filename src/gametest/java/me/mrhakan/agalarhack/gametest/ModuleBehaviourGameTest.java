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
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
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

    /** Set by the SafeWalk scenario: known-solid ground, and which way the pit lies. */
    private BlockPos ledge;

    /** Where the scene was built. Fixed for the run, unlike wherever a scenario left the player. */
    private BlockPos sceneBase;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            singleplayer.getConnection().waitForChunksRender();
            singleplayer.getServer().runOnServer(server ->
                    TestScene.build(singleplayer.getConnection().getServerPlayer()));
            context.waitTicks(20);
            sceneBase = singleplayer.getServer().computeOnServer(server ->
                    singleplayer.getConnection().getServerPlayer().blockPosition());

            autoTotem(context);
            autoArmor(context);
            cameraTweaks(context);
            autoWalk(context, singleplayer);
            safeWalk(context, singleplayer);
            autoRefill(context, singleplayer);
            inventoryCleaner(context, singleplayer);
            autoWeapon(context, singleplayer);
            betterChat(context, singleplayer);
            chatFilter(context, singleplayer);
            chatMentions(context, singleplayer);
            autoAccept(context, singleplayer);
            quietFrames(context, true);
            holeEsp(context, singleplayer);
            tracersAndNametags(context, singleplayer);
            quietFrames(context, false);

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
        this.ledge = digPit(context, singleplayer);
        BlockPos ledge = this.ledge;

        // The claim under test is "SafeWalk holds the edge", not "SafeWalk's ground gate is right".
        // Left on, that gate is checked afresh every tick, and a single tick where the walking player
        // is momentarily not on the ground lets them off - which is a race this scenario lost about
        // one run in eight. Turning it off removes the race without weakening what is asserted.
        configure(context, "SafeWalk", module -> module.settings.setSetting("onlyOnGround", false));

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
     * Parkour jumps at the edge rather than holding it, so the tell is upward movement.
     *
     * <p>Measured against the control walk rather than against a fixed number. Walking into the pit
     * raises the player a little all by itself - landing on the floor pushes them up around a third
     * of a block - and that figure is not something to hardcode, because it is exactly the kind of
     * incidental physics that changes with the scene. A vanilla jump is about 1.25 blocks, so the
     * difference between the two walks is unambiguous without needing to know either number.
     */
    private void parkour(ClientGameTestContext context, TestSingleplayerContext singleplayer,
            BlockPos ledge, Walk control) {
        toggle(context, "Parkour", true);
        Walk jumped = walkOffAndReport(context, singleplayer, ledge);
        toggle(context, "Parkour", false);

        double extra = jumped.rise() - control.rise();
        if (extra < 0.5 || jumped.rise() < 0.8) {
            throw new AssertionError("Parkour did not jump at the edge: the player rose "
                    + jumped.rise() + " blocks against the control walk's " + control.rise()
                    + ", a difference of " + extra + " where a jump is about 1.25");
        }
        LOGGER.info("  Parkour jumped {} blocks at the edge the control walk stepped off with {}",
                String.format("%.2f", jumped.rise()), String.format("%.2f", control.rise()));
    }

    /** How deep the pit is. Deep enough that falling in is unmistakable, shallow enough to survive. */
    private static final int PIT_DEPTH = 3;

    /**
     * The smallest number of changed pixels worth calling a drawing.
     *
     * <p>The real test is the ratio to the noise measured in the same run; this rules out a handful
     * of stray pixels when the scene is perfectly still and that floor is zero. Sixty is about a
     * short line or a few characters of text - small enough not to demand a particular size of
     * marker, large enough that nothing arrives there by accident.
     */
    private static final int DRAWN_PIXELS = 60;

    /**
     * Clears a 5x5 pit two blocks away and returns the standing spot facing it.
     *
     * <p>The pit gets a floor. A superflat world is four blocks thick, so digging even this far
     * without one punches straight through the bedrock and the "pit" becomes the void - which is
     * exactly what the first version did. It passed locally, because the player was still falling
     * when the sampling window closed, and failed on a slower CI runner where they fell far enough
     * to die. A test whose meaning depends on how fast the machine is has no meaning.
     */
    private BlockPos digPit(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        // Clean ground a fixed distance from the scene, not wherever the player happens to be.
        // AutoWalk runs immediately before this and stops somewhere different every time, so the pit
        // used to be dug on top of whatever TestScene had built there - and a chest or an ore block
        // at the ledge gives the player a 0.6 step up and a completely different walk. That is the
        // whole of the intermittent SafeWalk failure: roughly one run in ten the scenario was not
        // testing the thing it describes.
        BlockPos stand = sceneBase.offset(0, 0, -30);

        // Moved and dug in two steps, with a wait between. Doing both in one server call wrote the
        // pit into a chunk that was not loaded yet - fine on this machine, where the chunk was
        // already there, and not on a runner, where it was not.
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
        });
        singleplayer.getConnection().waitForChunksRender();
        context.waitTicks(40);

        BlockPos dug = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            ServerLevel level = player.level();
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
        singleplayer.getConnection().waitForChunksRender();
        context.waitTicks(20);
        return dug;
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
        // Logged for every walk: when one of these scenarios fails, the three walks' numbers side by
        // side are the difference between a diagnosis and a guess.
        LOGGER.info("    walk from y={} drop={} rise={} onGround={}",
                String.format("%.2f", startY), String.format("%.2f", lowest - startY),
                String.format("%.2f", highest - startY),
                context.computeOnClient(client -> client.player.onGround()));

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
     * With a weapon in the hotbar and something to hit, the selected slot should become the weapon.
     *
     * <p>The target is an armour stand rather than the scene's zombie. It is a living entity, which
     * is all the module asks for, and it does not walk away - a target that moves turns "the module
     * did not switch" and "the crosshair missed" into the same failure.
     */
    private void autoWeapon(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        final int swordSlot = 3;
        setInventory(singleplayer, slots -> {
            slots.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
            slots.setItem(swordSlot, new ItemStack(Items.DIAMOND_SWORD));
            slots.setSelectedSlot(0);
        });

        // Away from the pit, on ground the earlier scenarios did not dig out. Spawning the stand
        // where the player happened to be left it standing in the pit, three blocks below the
        // crosshair, which the guard below caught but which is the scenario's fault to avoid.
        BlockPos stand = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            BlockPos footing = ledge.offset(-10, 0, 0);
            player.teleportTo(footing.getX() + 0.5, footing.getY(), footing.getZ() + 0.5);
            BlockPos spot = footing.offset(2, 0, 0);
            if (EntityTypes.ARMOR_STAND.spawn(player.level(), spot, EntitySpawnReason.COMMAND) == null) {
                throw new AssertionError("could not spawn the armour stand at " + spot);
            }
            return spot;
        });
        context.waitTicks(20);
        // Its upper half, not its feet: a block-centre aim at the lower block looks below the body.
        context.getInput().lookAt(stand.above());
        context.waitTicks(10);

        // Aiming is this scenario's setup, not its claim. If the crosshair is not on the stand the
        // module is correct to do nothing, and reporting that as a module failure would be a lie.
        boolean aimed = context.computeOnClient(client ->
                client.hitResult instanceof net.minecraft.world.phys.EntityHitResult);
        if (!aimed) {
            throw new AssertionError("the crosshair is not on the armour stand, so AutoWeapon has "
                    + "nothing to react to; the scenario is broken, not the module");
        }

        Predicate<Minecraft> holdingSword =
                client -> client.player.getInventory().getSelectedSlot() == swordSlot;
        assertNotYet(context, holdingSword, "the sword slot was already selected before AutoWeapon ran");

        toggle(context, "AutoWeapon", true);
        boolean switched = settle(context, holdingSword);
        toggle(context, "AutoWeapon", false);
        if (!switched) {
            throw new AssertionError("AutoWeapon did not select the diamond sword with a living "
                    + "target under the crosshair");
        }
        LOGGER.info("  AutoWeapon selected the sword for the target under the crosshair");
    }

    /** Sends a line from the server so it arrives the way a real one does, and waits for it. */
    private static void say(ClientGameTestContext context, TestSingleplayerContext singleplayer, String text) {
        singleplayer.getServer().runOnServer(server ->
                server.getPlayerList().broadcastSystemMessage(Component.literal(text), false));
        context.waitTicks(10);
    }

    /** A timestamp is a prefix on the line, so the line has to be read back to see it. */
    private void betterChat(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        configure(context, "BetterChat", module -> {
            module.settings.setSetting("timestamps", true);
            module.settings.setSetting("seconds", true);
        });

        String plain = "betterchat control line";
        say(context, singleplayer, plain);
        boolean stampedWhileOff = context.computeOnClient(client ->
                ChatView.lines(client).stream().anyMatch(line -> line.contains(plain) && line.matches("^\\[\\d\\d:.*")));
        if (stampedWhileOff) {
            throw new AssertionError("a chat line was already timestamped before BetterChat was on");
        }

        toggle(context, "BetterChat", true);
        String stamped = "betterchat stamped line";
        say(context, singleplayer, stamped);
        toggle(context, "BetterChat", false);

        String line = context.computeOnClient(client -> ChatView.lines(client).stream()
                .filter(text -> text.contains(stamped)).findFirst().orElse(""));
        // HH:mm:ss in brackets, ahead of the server's own text.
        if (!line.matches("^\\[\\d\\d:\\d\\d:\\d\\d\\] .*" + java.util.regex.Pattern.quote(stamped) + ".*")) {
            throw new AssertionError("BetterChat did not timestamp the line; chat shows: " + line);
        }
        LOGGER.info("  BetterChat timestamped an arriving line");
    }

    /**
     * The filter hides a listed phrase and nothing else. The second half is the half that matters:
     * a filter that swallows everything would pass an assertion that only checks the listed line.
     */
    private void chatFilter(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        // Deliberately disjoint: none of these three is a substring of another. The first draft used
        // a control line that contained the hidden one, so the leak check found the control and
        // reported the module had failed to hide anything.
        String listed = "quidnunc";
        String control = "chatfilter control carries quidnunc while off";
        String hidden = "chatfilter later message also carrying quidnunc";
        String kept = "chatfilter innocent line";
        configure(context, "ChatFilter", module -> {
            module.settings.setSetting("hide", listed);
            // The scenario speaks through the server console, which arrives as a game message; the
            // module leaves those alone by default.
            module.settings.setSetting("gameMessages", true);
        });

        // Absence is only evidence if presence was possible. Without this, a broadcast that never
        // arrived at all would read exactly like a line the filter hid.
        say(context, singleplayer, control);
        boolean arrives = context.computeOnClient(client -> ChatView.contains(client, control));
        if (!arrives) {
            throw new AssertionError("a server line does not reach chat at all, so hiding one proves "
                    + "nothing; the scenario is broken, not the module");
        }

        toggle(context, "ChatFilter", true);
        say(context, singleplayer, hidden);
        say(context, singleplayer, kept);
        toggle(context, "ChatFilter", false);

        boolean leaked = context.computeOnClient(client -> ChatView.contains(client, hidden));
        if (leaked) {
            throw new AssertionError("ChatFilter did not hide a line matching a listed phrase");
        }
        boolean survived = context.computeOnClient(client -> ChatView.contains(client, kept));
        if (!survived) {
            throw new AssertionError("ChatFilter hid a line that matches nothing in its list; a "
                    + "filter that swallows everything is worse than no filter");
        }
        LOGGER.info("  ChatFilter hid the listed line and left the other alone");
    }

    /**
     * A mention is a notification, so the notification service is where the effect shows up.
     *
     * <p>Sent as player chat rather than from the server console, because the client deliberately
     * offers this module player chat only - a system line is a plugin talking, not someone
     * addressing you. The first draft used a server broadcast and the module was right to ignore it.
     *
     * <p>The trigger is a keyword from another speaker. Both halves are forced: the module ignores
     * your own messages, so the line cannot come from the local player, and with only one real
     * player in the world the server has to speak for a second one.
     */
    private void chatMentions(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        String keyword = "zarfblat";
        configure(context, "ChatMentions", module -> {
            module.settings.setSetting("keywords", keyword);
            module.settings.setSetting("ownName", false);
            // The cue would reach for an audio device this machine does not have.
            module.settings.setSetting("sound", false);
            module.settings.setSetting("cooldown", 0.0);
        });

        // A mention surfaces as a toast, and the Notifications module is what makes toasts exist:
        // its onDisable switches the whole notification service off, and the lifecycle test toggled
        // it off earlier in this run. Without this the service silently drops every publish.
        toggle(context, "Notifications", true);

        somebodySays(context, singleplayer, "Someone", "control line mentioning " + keyword);
        if (mentionNotified(context)) {
            throw new AssertionError("a mention was reported before ChatMentions was enabled");
        }

        toggle(context, "ChatMentions", true);
        somebodySays(context, singleplayer, "Someone", "second line mentioning " + keyword);
        boolean noticed = mentionNotified(context);
        toggle(context, "ChatMentions", false);
        toggle(context, "Notifications", false);
        if (!noticed) {
            throw new AssertionError("ChatMentions did not report a chat line containing its "
                    + "configured keyword");
        }
        LOGGER.info("  ChatMentions reported a keyword in player chat");
    }

    /**
     * Chat attributed to somebody who is not the local player.
     *
     * <p>Sending it from the player instead does not work, and the module is right about that: a
     * player chat line arrives rendered as {@code <Player0> ...}, which contains the player's own
     * name, and ChatMentions deliberately refuses to treat your own message as a mention of you.
     * There is only one real player in a test world, so the server speaks for a second one.
     */
    private static void somebodySays(ClientGameTestContext context, TestSingleplayerContext singleplayer,
            String speaker, String text) {
        singleplayer.getServer().runOnServer(server -> server.getPlayerList().broadcastChatMessage(
                // The real player's id, with somebody else's name bound to the line. A made-up id is
                // rejected by the client as a chat validation error - it only accepts messages from
                // senders it knows about - and the display name is all the module reads anyway.
                PlayerChatMessage.unsigned(singleplayer.getConnection().getServerPlayer().getUUID(), text),
                server.createCommandSourceStack(),
                ChatType.bind(ChatType.CHAT, server.registryAccess(), Component.literal(speaker))));
        context.waitTicks(10);
    }

    private static boolean mentionNotified(ClientGameTestContext context) {
        return context.computeOnClient(client ->
                me.mrhakan.agalarhack.services.ClientServices.require(
                                me.mrhakan.agalarhack.services.NotificationService.class)
                        .visible().stream().anyMatch(notice -> notice.text().contains("mentioned in chat")));
    }

    /**
     * The strongest end-to-end check here: the module answers by sending a real command, so the
     * assertion is that the server echoed it back. Nothing about that can be faked client-side.
     *
     * <p>The reply is {@code /me} rather than {@code /say} because it needs no permission level - a
     * reply the server refuses would look exactly like a module that never fired.
     */
    private void autoAccept(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        String requester = "Requester";
        String echo = "autoaccept answered a request";
        configure(context, "AutoAccept", module -> {
            module.settings.setSetting("phrases", "wants to teleport");
            module.settings.setSetting("reply", "/me " + echo);
            module.settings.setSetting("friendsOnly", false);
            module.settings.setSetting("allowedNames", requester);
            module.settings.setSetting("cooldownSeconds", 1.0);
        });

        String request = requester + " wants to teleport to you";
        say(context, singleplayer, request);
        if (context.computeOnClient(client -> ChatView.contains(client, echo))) {
            throw new AssertionError("the reply appeared in chat before AutoAccept was enabled");
        }

        toggle(context, "AutoAccept", true);
        say(context, singleplayer, request);
        context.waitTicks(20);
        boolean answered = context.computeOnClient(client -> ChatView.contains(client, echo));
        toggle(context, "AutoAccept", false);
        if (!answered) {
            throw new AssertionError("AutoAccept did not send its reply; the server never echoed \""
                    + echo + "\" back into chat");
        }
        LOGGER.info("  AutoAccept answered a listed requester and the server echoed the command");
    }

    /**
     * Everything that moves the picture without a module doing it.
     *
     * <p>Clouds drift whatever the world clock does; on their own they put the noise floor at 1.77.
     * The HUD is worse than noise: the enabled-module list changes whenever a module is toggled, so
     * leaving it on would let every render scenario pass with nothing drawn in the world at all.
     *
     * <p>This is shared setup rather than per-scenario because the first version toggled the HUD
     * inside one scenario and restored it at the end, which left every later one measuring the HUD.
     * That showed up as a noise floor of 0.85 against a signal of 0.87 - the control refusing to
     * call it evidence, correctly.
     */
    private static void quietFrames(ClientGameTestContext context, boolean quiet) {
        if (quiet) {
            context.runOnClient(client ->
                    client.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF));
        }
        // F1 toggles; pressing it again on the way out puts the HUD back.
        context.getInput().pressKey(options -> options.keyToggleGui);
        context.waitTicks(20);
    }

    /**
     * The first render module checked by what it draws rather than by what it stores.
     *
     * <p>An overlay leaves nothing behind to assert on, so the evidence is the picture. See
     * {@link Frames} for why nothing is compared against a stored reference image. The noise
     * measurement here is the control, and it is a real one: if the scene will not hold still, two
     * frames taken under identical conditions differ as much as the module does and the scenario
     * reports that instead of passing.
     *
     * <p>What this proves is that HoleESP draws something where a hole is. It does not prove the
     * marker is the right shape, colour or place.
     *
     * <p>The camera is inside the hole for a reason that cost several runs to find. These overlays
     * are depth tested: seen from outside, the marker lies behind the near rim and the frame does
     * not change by a single pixel, which reads exactly like a module that draws nothing. Looking
     * down the shaft at it is the difference between measuring the module and measuring occlusion.
     */
    private void holeEsp(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        BlockPos hole = stillSceneWithHole(context, singleplayer);
        // Straight down the shaft at the marker on the floor.
        context.getInput().lookAt(0.0f, 89.0f);

        context.waitTicks(40);

        // Both windows are the same length on purpose. Measuring noise over ten ticks and signal
        // over sixty compares unequal things: anything that drifts with time - clouds, most
        // obviously - scales with the window, and the first version of this read six times the
        // cloud drift as a healthy signal from a module that was in fact drawing nothing.
        final int window = 60;
        java.nio.file.Path before = context.takeScreenshot("holeesp-off-1");
        context.waitTicks(window);
        java.nio.file.Path stillOff = context.takeScreenshot("holeesp-off-2");
        double noise = Frames.difference(before, stillOff);

        toggle(context, "HoleESP", true);
        // The scanner works through its budget over several ticks before anything is marked.
        context.waitTicks(window);
        java.nio.file.Path on = context.takeScreenshot("holeesp-on");
        toggle(context, "HoleESP", false);
        double signal = Frames.difference(stillOff, on);

        LOGGER.info("    HoleESP frames: noise={} signal={}",
                String.format("%.3f", noise), String.format("%.3f", signal));
        if (noise > 1.0) {
            throw new AssertionError("two frames taken with nothing changed differ by " + noise
                    + "; the scene will not hold still, so no screenshot scenario here means anything");
        }
        if (signal < Math.max(0.5, noise * 5)) {
            throw new AssertionError("HoleESP changed the picture by " + signal + " against a noise "
                    + "floor of " + noise + "; it drew nothing over a hole it should have marked");
        }
        LOGGER.info("  HoleESP drew over the hole");
    }

    /**
     * Two overlays with something to point at, each measured where it actually draws.
     *
     * <p>Both need a Mob - the groups these modules select on are player, item and Mob, and nothing
     * else - that stays put and does not burn in daylight, so a pig with its AI switched off. It
     * cannot be made invisible to stop the model animating: the mod never offers invisible entities
     * to these overlays at all, and the target count drops to zero.
     *
     * <p>The two are framed differently on purpose, because they draw in different places. A tracer
     * is a line from the edge of the screen to the target, so the target goes far away where its
     * animation is a handful of pixels and the line is still full length. A nametag is a small label
     * directly above the entity, so the target comes close where the label is large, and only the
     * band above the crosshair is measured - which is where the label is and where the animating
     * body is not.
     */
    private void tracersAndNametags(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        configure(context, "Tracers", module -> {
            module.settings.setSetting("players", true);
            module.settings.setSetting("hostiles", true);
            module.settings.setSetting("passives", true);
            // Lines start at the bottom of the screen. With the default centre origin and the camera
            // pointed at the target, a tracer runs from the middle of the screen to the middle of
            // the screen: no length, and a signal of nothing.
            module.settings.setSetting("origin", "bottom");
        });
        configure(context, "Nametags", module -> {
            module.settings.setSetting("players", true);
            module.settings.setSetting("mobs", true);
        });

        BlockPos far = pigAt(context, singleplayer, 30);
        context.getInput().lookAt(far);
        context.waitTicks(40);
        drawsSomething(context, "Tracers", "a line to the pig", 0.25, 0.75);

        BlockPos near = pigAt(context, singleplayer, 6);
        // At the pig itself. A pig is about nine tenths of a block tall, so its tag sits barely
        // above its back - aiming two blocks up, as for something person-sized, puts the crosshair
        // above the tag and the tag below the band being measured.
        context.getInput().lookAt(near);
        // HUD back on for this one. Nametags are labels rather than world geometry, and F1 takes
        // them with it - with the HUD hidden the module draws nothing measurable at all. The band
        // measured here is the middle of the screen above the crosshair, which no HUD element
        // occupies: the module list is top right, the hotbar bottom centre, both outside it.
        context.getInput().pressKey(options -> options.keyToggleGui);
        context.waitTicks(40);
        drawsSomething(context, "Nametags", "a tag above the pig", 0.10, 0.50);
        context.getInput().pressKey(options -> options.keyToggleGui);
    }

    /** Clean ground, nothing else alive, and one motionless pig the given distance ahead. */
    private BlockPos pigAt(ClientGameTestContext context, TestSingleplayerContext singleplayer,
            int distance) {
        moveThere(context, singleplayer, sceneBase.offset(0, 0, 60));
        return singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            ServerLevel level = player.level();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof ServerPlayer)) entity.discard();
            }
            BlockPos target = player.blockPosition().offset(0, 0, distance);
            var pig = EntityTypes.PIG.spawn(level, target, EntitySpawnReason.COMMAND);
            if (pig == null) {
                throw new AssertionError("could not spawn the pig at " + target);
            }
            pig.setNoAi(true);
            pig.setPersistenceRequired();
            return target;
        });
    }

    /**
     * The shared shape of every render scenario: how still the picture is, then how much the module
     * moves it. Both windows are the same length, for the reason given in {@link #holeEsp}.
     */
    private void drawsSomething(ClientGameTestContext context, String name, String expected,
            double fromHeight, double toHeight) {
        final int window = 60;
        String slug = name.toLowerCase(java.util.Locale.ROOT);
        java.nio.file.Path first = context.takeScreenshot(slug + "-off-1");
        context.waitTicks(window);
        java.nio.file.Path second = context.takeScreenshot(slug + "-off-2");
        int noise = Frames.changedPixels(first, second, fromHeight, toHeight);

        toggle(context, name, true);
        context.waitTicks(window);
        java.nio.file.Path on = context.takeScreenshot(slug + "-on");
        toggle(context, name, false);
        int signal = Frames.changedPixels(second, on, fromHeight, toHeight);

        LOGGER.info("    {} pixels: noise={} signal={}", name, noise, signal);
        if (noise > DRAWN_PIXELS) {
            throw new AssertionError(name + ": " + noise + " pixels changed with nothing switched "
                    + "on, so the scene will not hold still and this scenario means nothing");
        }
        if (signal < Math.max(DRAWN_PIXELS, noise * 5)) {
            throw new AssertionError(name + " changed " + signal + " pixels against a noise floor of "
                    + noise + "; it drew no " + expected);
        }
        LOGGER.info("  {} drew {}", name, expected);
    }

    /**
     * Builds a fresh hole on untouched ground and empties the world of everything that moves.
     *
     * <p>Both halves matter. A hole left over from an earlier scenario may have been walked through
     * or built over, and a single wandering mob changes every frame by itself, which would make the
     * noise floor swallow the signal.
     */
    private static BlockPos stillSceneWithHole(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        BlockPos hole = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            ServerLevel level = player.level();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof ServerPlayer)) entity.discard();
            }

            BlockPos centre = ledgeOrSpawn(player).offset(0, 0, 20);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy >= -1; dy--) {
                        level.setBlockAndUpdate(centre.offset(dx, dy, dz), Blocks.OBSIDIAN.defaultBlockState());
                    }
                }
            }
            level.setBlockAndUpdate(centre, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(centre.below(), Blocks.AIR.defaultBlockState());

            // Standing in the hole itself. The marker is a slab across its floor, and from
            // outside the rim occludes it: the sight line enters the obsidian before it
            // reaches the slab, so the module draws correctly and nothing shows.
            player.teleportTo(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
            player.setDeltaMovement(Vec3.ZERO);
            return centre;
        });
        singleplayer.getConnection().waitForChunksRender();
        context.waitTicks(40);
        return hole;
    }

    /**
     * Puts the player somewhere and waits until the world there actually exists.
     *
     * <p>Every scenario that builds scenery must do this first. Writing blocks or spawning entities
     * into a chunk the server has not loaded silently does nothing, and what follows is a scenario
     * measuring an empty field and blaming the module. That mistake was made four times here before
     * it was worth a helper, and twice it turned CI red.
     */
    private static void moveThere(ClientGameTestContext context, TestSingleplayerContext singleplayer,
            BlockPos where) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            player.teleportTo(where.getX() + 0.5, where.getY(), where.getZ() + 0.5);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
        });
        singleplayer.getConnection().waitForChunksRender();
        context.waitTicks(40);
    }

    private static BlockPos ledgeOrSpawn(ServerPlayer player) {
        return player.blockPosition();
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
