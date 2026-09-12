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
import net.minecraft.world.level.gamerules.GameRules;
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
            singleplayer.getServer().runOnServer(server -> {
                // Random ticks are the one thing in an empty superflat world that still changes
                // blocks, and every scanning module restarts its sweep when a block near it
                // changes. Grass dying under a roof is enough to empty a result set between the
                // scan and the screenshot, which reads exactly like a module that drew nothing.
                server.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, server);
                // The sun moves otherwise, and a sky that is a shade different sixty ticks later is
                // noise in every frame comparison that can see any of it.
                server.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
                TestScene.build(singleplayer.getConnection().getServerPlayer());
            });
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
            performance(context);
            serverInfo(context);
            critInfo(context, singleplayer);
            elytraInfo(context, singleplayer);
            totemTracker(context, singleplayer);
            combatHistory(context, singleplayer);
            baseFinder(context, singleplayer);
            projectileWarning(context, singleplayer);
            autoFish(context, singleplayer);
            hudScale(context, singleplayer);
            clickGuiTransition(context);
            quietFrames(context, true);
            holeEsp(context, singleplayer);
            tracersAndNametags(context, singleplayer);
            itemEsp(context, singleplayer);
            breadcrumbs(context, singleplayer);
            waypoints(context, singleplayer);
            spawnEsp(context, singleplayer);
            projectileEsp(context, singleplayer);
            quietFrames(context, false);
            autoRespawn(context, singleplayer);

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
        // A walk that rises before it falls is a walk where the player left the ground before the
        // edge, and vanilla only clamps a player who is above ground - Player.maybeBackOffFromEdge
        // tests isAboveGround(maxUpStep) alongside the sneak gate this module forces. So SafeWalk
        // cannot hold an already-airborne player, by vanilla's rule rather than as a defect, and a
        // walk like that tells nothing apart. It is taken again rather than reported either way.
        // Seen locally at rise=0.60, exactly maxUpStep, on a machine running four Gradle daemons;
        // the same commit was green on CI.
        if (held.drop() < -0.5 && held.rise() > 0.25) {
            LOGGER.info("    retaking the SafeWalk walk: the player rose {} before falling, so they "
                    + "were airborne at the edge and vanilla's clamp never applied",
                    String.format("%.2f", held.rise()));
            held = walkOffAndReport(context, singleplayer, ledge);
        }
        toggle(context, "SafeWalk", false);
        if (held.drop() < -0.5) {
            throw new AssertionError("SafeWalk let the player drop " + held.drop()
                    + " blocks (rising " + held.rise() + " first) off a ledge the control walk also "
                    + "fell from");
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
        return context.computeOnClient(notice("mentioned in chat")::test);
    }

    /** A toast is showing whose text contains this fragment. */
    private static Predicate<Minecraft> notice(String fragment) {
        return client -> me.mrhakan.agalarhack.services.ClientServices.require(
                        me.mrhakan.agalarhack.services.NotificationService.class)
                .visible().stream().anyMatch(showing -> showing.text().contains(fragment));
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
        // Waited for, not timed. The scanner works through a budget spread over ticks, and how many
        // it needs is not fixed - a run where sixty were not enough photographed an unmarked hole
        // and reported that the module had drawn nothing. Polling for the result separates "the
        // scanner has not finished" from "the module drew nothing", which are different failures.
        boolean found = false;
        for (int waited = 0; waited < window * 4 && !found; waited += 10) {
            context.waitTicks(10);
            found = context.computeOnClient(client ->
                    !((me.mrhakan.agalarhack.module.render.HoleESP)
                            AgalarHackClient.moduleManager.getModule("HoleESP")).results().isEmpty());
        }
        if (!found) {
            toggle(context, "HoleESP", false);
            throw new AssertionError("HoleESP never reported the hole the scenario dug, so there was "
                    + "nothing for it to draw; the scan did not finish rather than the module failing");
        }
        // The same window as the noise measurement, now that there is something to photograph.
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

    /**
     * A dropped item, measured by the label the module writes above it.
     *
     * <p>Same shape as the nametag scenario and for the same reason: a drop bobs and spins by
     * itself, so measuring the band above it counts the label and excludes the animation rather
     * than tolerating it. The drop is set never to be picked up, which would otherwise end the
     * scenario halfway through by removing the thing being looked at.
     */
    private void itemEsp(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        configure(context, "ItemESP", module -> {
            module.settings.setSetting("labels", true);
            module.settings.setSetting("boxes", true);
        });

        moveThere(context, singleplayer, sceneBase.offset(0, 0, 90));
        BlockPos drop = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            ServerLevel level = player.level();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof ServerPlayer)) entity.discard();
            }
            BlockPos where = player.blockPosition().offset(0, 0, 5);
            var item = new net.minecraft.world.entity.item.ItemEntity(level,
                    where.getX() + 0.5, where.getY(), where.getZ() + 0.5,
                    new ItemStack(Items.DIAMOND, 3));
            item.setNeverPickUp();
            item.setUnlimitedLifetime();
            item.setDeltaMovement(Vec3.ZERO);
            if (!level.addFreshEntity(item)) {
                throw new AssertionError("could not drop an item at " + where);
            }
            return where;
        });
        context.getInput().lookAt(drop);
        context.waitTicks(40);
        drawsSomething(context, "ItemESP", "a label over the dropped diamonds", 0.10, 0.50);
    }

    /**
     * The trail is built by walking, so this one runs backwards: the module is on while the player
     * moves, and switched off afterwards to get the frame without it.
     *
     * <p>Every other scenario measures off, then on. Here that is impossible - there is nothing to
     * draw until the player has been somewhere - so the two off frames come last and the comparison
     * runs the other way. A difference does not care which frame came first.
     */
    private void breadcrumbs(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        moveThere(context, singleplayer, sceneBase.offset(0, 0, 120));
        configure(context, "Breadcrumbs", module -> module.settings.setSetting("minDistance", 0.5));

        toggle(context, "Breadcrumbs", true);
        context.getInput().holdKeyFor(options -> options.keyUp, 60);
        context.waitTicks(20);
        // Turned around, so the trail just walked is in front of the camera rather than behind it.
        context.getInput().lookAt(0.0f, 10.0f);
        context.runOnClient(client -> client.player.setYRot(client.player.getYRot() + 180.0f));
        context.waitTicks(20);

        java.nio.file.Path withTrail = context.takeScreenshot("breadcrumbs-on");
        toggle(context, "Breadcrumbs", false);
        context.waitTicks(20);
        java.nio.file.Path plain = context.takeScreenshot("breadcrumbs-off-1");
        context.waitTicks(60);
        java.nio.file.Path plainAgain = context.takeScreenshot("breadcrumbs-off-2");

        int noise = Frames.changedPixels(plain, plainAgain, 0.25, 0.75);
        int signal = Frames.changedPixels(plainAgain, withTrail, 0.25, 0.75);
        LOGGER.info("    Breadcrumbs pixels: noise={} signal={}", noise, signal);
        assertDrew("Breadcrumbs", "trail behind a player that had just walked", noise, signal);
        LOGGER.info("  Breadcrumbs drew the trail the player had just walked");
    }

    /**
     * A waypoint marker, with its beam, twenty blocks ahead.
     *
     * <p>The beam is a column two hundred blocks tall, which makes this the least ambiguous drawing
     * any of these modules produce. The waypoint is added through the service the command uses, so
     * the scenario exercises the same path a player would.
     */
    private void waypoints(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        configure(context, "Waypoints", module -> {
            module.settings.setSetting("beams", true);
            module.settings.setSetting("labels", true);
        });

        moveThere(context, singleplayer, sceneBase.offset(0, 0, 150));
        BlockPos marker = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            for (Entity entity : player.level().getAllEntities()) {
                if (!(entity instanceof ServerPlayer)) entity.discard();
            }
            return player.blockPosition().offset(0, 0, 20);
        });
        context.runOnClient(client -> {
            var service = me.mrhakan.agalarhack.services.ClientServices.require(
                    me.mrhakan.agalarhack.services.WaypointService.class);
            service.remove("gametest", me.mrhakan.agalarhack.module.render.Waypoints.currentDimension(client));
            service.add(me.mrhakan.agalarhack.services.Waypoint.of("gametest",
                            marker.getX(), marker.getY(), marker.getZ(),
                            me.mrhakan.agalarhack.module.render.Waypoints.currentDimension(client))
                    .withBeam(true));
        });
        context.getInput().lookAt(marker);
        context.waitTicks(40);
        drawsSomething(context, "Waypoints", "a marker or beam at the saved position", 0.25, 0.75);
    }

    /**
     * SpawnESP marks where light allows a spawn, so the scenario has to make somewhere dark.
     *
     * <p>A superflat world at noon has no such place: the surface is lit everywhere and the module
     * would correctly mark nothing. The player is sealed into a roofed box instead, which is the
     * one situation this module exists for.
     */
    private void spawnEsp(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        // A short sweep on purpose. The default radius is forty thousand positions, which is several
        // ticks of a shared budget the other enabled scanners are also drawing on, and the frame is
        // taken at a fixed moment rather than when the sweep happens to finish.
        configure(context, "SpawnESP", module -> {
            module.settings.setSetting("horizontalRange", 12.0);
            module.settings.setSetting("verticalRange", 4.0);
        });

        moveThere(context, singleplayer, sceneBase.offset(0, 0, 180));
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            ServerLevel level = player.level();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof ServerPlayer)) entity.discard();
            }
            BlockPos centre = player.blockPosition();
            // A sealed shell: walls, and a roof three blocks up so there is standing room under it.
            // The floor is replaced too, because superflat's grass dies once it is roofed over and
            // each death is a block update that sends the scan back to the start.
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    level.setBlockAndUpdate(centre.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(centre.offset(dx, 3, dz), Blocks.OBSIDIAN.defaultBlockState());
                    for (int dy = 0; dy <= 2; dy++) {
                        boolean wall = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                        level.setBlockAndUpdate(centre.offset(dx, dy, dz), wall
                                ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                }
            }
        });
        singleplayer.getConnection().waitForChunksRender();
        // Long enough for the light engine to darken the sealed volume before anything is scanned.
        context.waitTicks(80);
        // At the floor, where the markers go.
        context.getInput().lookAt(0.0f, 60.0f);
        context.waitTicks(20);
        drawsSomething(context, "SpawnESP", "markers on a dark floor", 0.25, 0.75,
                client -> AgalarHackClient.moduleManager.getModule("SpawnESP")
                        instanceof me.mrhakan.agalarhack.module.render.SpawnESP spawn
                        && !spawn.results().isEmpty());
    }

    /**
     * A module whose whole job is to reconfigure a shared service, so the service is the evidence.
     *
     * <p>The restore half is the one worth asserting. A client left on the low profile after the
     * module was switched off would scan slowly for the rest of the session, and nothing on screen
     * would say why.
     */
    private void performance(ClientGameTestContext context) {
        var builtIn = me.mrhakan.agalarhack.services.scanning.ScanBudgets.BALANCED;
        var wanted = me.mrhakan.agalarhack.services.scanning.ScanBudgets.forProfile("low");
        var before = scanBudgets(context);
        if (!builtIn.equals(before)) {
            throw new AssertionError("the shared scanner was already off its built-in ceiling (" + before
                    + ") before Performance ran; the scenario proves nothing in that state");
        }

        configure(context, "Performance", module -> module.settings.setSetting("scanBudget", "low"));
        toggle(context, "Performance", true);
        context.waitTicks(8);
        var applied = scanBudgets(context);
        toggle(context, "Performance", false);
        context.waitTicks(8);
        var restored = scanBudgets(context);

        if (!wanted.equals(applied)) {
            throw new AssertionError("Performance on the low profile left the shared scanner at "
                    + applied + " instead of " + wanted);
        }
        if (!builtIn.equals(restored)) {
            throw new AssertionError("Performance left the shared scanner at " + restored
                    + " after being disabled instead of restoring " + builtIn);
        }
        LOGGER.info("  Performance lowered the shared scanning ceiling and gave it back");
    }

    private static me.mrhakan.agalarhack.services.scanning.ScanBudgets scanBudgets(ClientGameTestContext context) {
        return context.computeOnClient(client -> me.mrhakan.agalarhack.services.ClientServices.require(
                me.mrhakan.agalarhack.services.ScannerService.class).budgets());
    }

    /**
     * The tick figure is an estimate built from how far apart the server's world-time packets
     * arrive, so the scenario waits for real packets rather than feeding the module anything.
     *
     * <p>Ping is deliberately not asserted: the integrated server reports a latency of zero, which
     * the module correctly declines to record as a sample.
     */
    private void serverInfo(ClientGameTestContext context) {
        Predicate<Minecraft> estimating = client ->
                AgalarHackClient.moduleManager.getModule("ServerInfo")
                        instanceof me.mrhakan.agalarhack.module.misc.ServerInfo info
                        && info.tickEstimate().hasEstimate()
                        && info.getDisplayName().startsWith("ServerInfo [");
        assertNotYet(context, estimating, "ServerInfo already had a tick estimate before it was enabled");

        toggle(context, "ServerInfo", true);
        // World time arrives once every twenty ticks and two of them make the first interval.
        boolean estimated = settle(context, estimating, 200);
        double tps = context.computeOnClient(client ->
                ((me.mrhakan.agalarhack.module.misc.ServerInfo) AgalarHackClient.moduleManager
                        .getModule("ServerInfo")).tickEstimate().average());
        toggle(context, "ServerInfo", false);

        if (!estimated) {
            throw new AssertionError("ServerInfo produced no tick estimate within 200 ticks of a running server");
        }
        // Wide on purpose: this asserts the estimate is a rate rather than nonsense, not that a
        // headless CI runner hits twenty.
        if (tps < 1.0 || tps > 60.0) {
            throw new AssertionError("ServerInfo estimated " + tps + " tps for an idle integrated server");
        }
        LOGGER.info("  ServerInfo estimated {} tps from the server's own time packets",
                String.format(java.util.Locale.ROOT, "%.1f", tps));
    }

    /**
     * The crit rule read against the player's real state: no crit with both feet on the ground, a
     * crit on the way down. Nothing is fed to the module — it reads the same player the game does,
     * which is the only way to tell a correct rule from one that always answers the same.
     */
    private void critInfo(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        Predicate<Minecraft> critting = client ->
                AgalarHackClient.moduleManager.getModule("CritInfo")
                        instanceof me.mrhakan.agalarhack.module.combat.CritInfo crit
                        && crit.critReady()
                        && "CritInfo [ready]".equals(crit.getDisplayName());

        moveThere(context, singleplayer, sceneBase.offset(0, 0, 210));
        toggle(context, "CritInfo", true);
        context.waitTicks(20);
        if (context.computeOnClient(critting::test)) {
            toggle(context, "CritInfo", false);
            throw new AssertionError("CritInfo said a hit would crit while the player stood still on the ground");
        }

        // Creative, so thirty blocks is a fall rather than a death.
        BlockPos above = sceneBase.offset(0, 30, 210);
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            player.teleportTo(above.getX() + 0.5, above.getY(), above.getZ() + 0.5);
            player.setDeltaMovement(Vec3.ZERO);
        });
        boolean crits = settle(context, critting, 60);
        toggle(context, "CritInfo", false);
        if (!crits) {
            throw new AssertionError("CritInfo never reported a critical while the player was falling "
                    + "thirty blocks, which is exactly the state 26.2 crits in");
        }
        context.waitTicks(40);
        LOGGER.info("  CritInfo told a grounded player from a falling one");
    }

    /**
     * A nearly worn-out elytra should be news before the flight, not during it.
     *
     * <p>The durability figure is read off the worn item rather than guessed, so the assertion is on
     * the exact number: a module that warned with the right text and the wrong count would be no
     * use to somebody deciding whether to launch.
     */
    private void elytraInfo(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        // Toasts only exist while the Notifications module is on; the lifecycle test left it off.
        toggle(context, "Notifications", true);

        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            ItemStack elytra = new ItemStack(Items.ELYTRA);
            // Ten uses left, well under the twenty-use default threshold.
            elytra.setDamageValue(elytra.getMaxDamage() - 10);
            player.setItemSlot(EquipmentSlot.CHEST, elytra);
        });
        context.waitTicks(20);
        assertNotYet(context, notice("Elytra at "), "a worn elytra was reported before ElytraInfo was enabled");

        toggle(context, "ElytraInfo", true);
        boolean warned = settle(context, notice("Elytra at 10 uses"));
        String label = context.computeOnClient(client ->
                AgalarHackClient.moduleManager.getModule("ElytraInfo").getDisplayName());
        toggle(context, "ElytraInfo", false);
        singleplayer.getServer().runOnServer(server -> singleplayer.getConnection().getServerPlayer()
                .setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY));

        if (!warned) {
            throw new AssertionError("ElytraInfo did not warn about an elytra with ten uses left within "
                    + SETTLE_TICKS + " ticks");
        }
        if (!label.startsWith("ElytraInfo [10 dur")) {
            throw new AssertionError("ElytraInfo warned but its module-list label read " + label
                    + " rather than the ten uses left on the worn elytra");
        }
        LOGGER.info("  ElytraInfo warned about a worn elytra and counted its uses");
    }

    /**
     * A totem is popped for real: the player is put in survival, handed one, and dealt more damage
     * than they have health.
     *
     * <p>Ordinary magic damage rather than {@code kill()}, which is tagged as bypassing
     * invulnerability and would take the player straight past the totem the scenario is about.
     */
    private void totemTracker(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        toggle(context, "Notifications", true);
        moveThere(context, singleplayer, sceneBase.offset(0, 0, 360));
        String who = context.computeOnClient(client -> client.player.getName().getString());

        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            player.setGameMode(GameType.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        });
        context.waitTicks(20);

        toggle(context, "TotemTracker", true);
        Predicate<Minecraft> counted = client ->
                AgalarHackClient.moduleManager.getModule("TotemTracker")
                        instanceof me.mrhakan.agalarhack.module.combat.TotemTracker tracker
                        && tracker.popsFor(who) == 1
                        && "TotemTracker [1]".equals(tracker.getDisplayName());
        if (context.computeOnClient(counted::test)) {
            toggle(context, "TotemTracker", false);
            throw new AssertionError("TotemTracker counted a pop before any totem had been used");
        }

        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            player.hurtServer(player.level(), player.level().damageSources().magic(), 1000.0f);
        });
        boolean sawPop = settle(context, counted);
        boolean announced = context.computeOnClient(notice("popped 1 totem (seen)")::test);
        toggle(context, "TotemTracker", false);

        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            player.setHealth(player.getMaxHealth());
            player.setGameMode(GameType.CREATIVE);
            // A totem leaves regeneration and absorption running for the next forty-five seconds,
            // and their particles drift through the camera. The render scenarios that follow
            // measure a still frame against a still frame, and that is not still.
            player.removeAllEffects();
        });
        context.waitTicks(10);

        if (!sawPop) {
            throw new AssertionError("TotemTracker did not count the totem the player just popped within "
                    + SETTLE_TICKS + " ticks");
        }
        if (!announced) {
            throw new AssertionError("TotemTracker counted the pop but published no notification about it");
        }
        LOGGER.info("  TotemTracker counted a totem the player actually popped");
    }

    /**
     * The log fills from combat, not from being switched on. The control half is the point: the
     * module sits enabled with a zombie in front of it and records nothing until Aura, which is what
     * sets the shared target, is switched on too.
     */
    private void combatHistory(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        moveThere(context, singleplayer, sceneBase.offset(0, 0, 240));
        BlockPos where = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            for (Entity entity : player.level().getAllEntities()) {
                if (!(entity instanceof ServerPlayer)) entity.discard();
            }
            BlockPos spot = player.blockPosition().offset(3, 0, 0);
            var zombie = EntityTypes.ZOMBIE.spawn(player.level(), spot, EntitySpawnReason.COMMAND);
            if (zombie == null) throw new AssertionError("could not spawn the zombie at " + spot);
            // Mobs never target a creative player - that is what EntitySelector.NO_CREATIVE_OR_SPECTATOR
            // is for - so a zombie with its AI on does not walk towards the player, it wanders, and
            // roughly one run in five it wanders outside Aura's four-block reach before the window
            // closes. Standing still is the whole of its job here.
            zombie.setNoAi(true);
            return spot;
        });
        context.waitTicks(20);
        context.getInput().lookAt(where.above());

        Predicate<Minecraft> remembered = client ->
                AgalarHackClient.moduleManager.getModule("CombatHistory")
                        instanceof me.mrhakan.agalarhack.module.combat.CombatHistory history
                        && history.log().size() > 0
                        && history.getDisplayName().startsWith("CombatHistory [");

        toggle(context, "CombatHistory", true);
        context.waitTicks(40);
        if (context.computeOnClient(remembered::test)) {
            toggle(context, "CombatHistory", false);
            throw new AssertionError("CombatHistory recorded a target with nothing attacking; it is "
                    + "logging something other than combat");
        }

        // Aura reaches four blocks. If the zombie is not inside that, Aura is right to do nothing and
        // reporting it as CombatHistory's failure would be a lie about which half broke.
        double gap = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            double nearest = Double.MAX_VALUE;
            for (Entity entity : player.level().getAllEntities()) {
                if (entity instanceof ServerPlayer) continue;
                nearest = Math.min(nearest, player.distanceTo(entity));
            }
            return nearest;
        });
        if (gap > 4.0) {
            toggle(context, "CombatHistory", false);
            throw new AssertionError("the nearest thing to fight is " + gap + " blocks away, outside "
                    + "Aura's reach, so there is nothing for CombatHistory to record; the scenario is "
                    + "broken, not the module");
        }

        toggle(context, "Aura", true);
        boolean logged = settle(context, remembered);
        toggle(context, "Aura", false);
        toggle(context, "CombatHistory", false);
        singleplayer.getServer().runOnServer(server -> {
            for (Entity entity : singleplayer.getConnection().getServerPlayer().level().getAllEntities()) {
                if (!(entity instanceof ServerPlayer)) entity.discard();
            }
        });

        if (!logged) {
            throw new AssertionError("CombatHistory logged nothing while Aura was attacking a zombie "
                    + "three blocks away");
        }
        LOGGER.info("  CombatHistory remembered the target Aura fought");
    }

    /**
     * A cluster of storage is built and BaseFinder is asked to notice it.
     *
     * <p>The module draws on StorageESP's cache rather than scanning itself, and the first half of
     * this checks it says so plainly instead of looking broken when that module is off.
     */
    private void baseFinder(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        toggle(context, "Notifications", true);
        moveThere(context, singleplayer, sceneBase.offset(0, 0, 270));
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            ServerLevel level = player.level();
            BlockPos centre = player.blockPosition().offset(6, 0, 0);
            // Spaced two apart so they stay nine separate chests rather than merging into doubles,
            // and well inside the twelve-block default cluster radius.
            for (int dx = -2; dx <= 2; dx += 2) {
                for (int dz = -2; dz <= 2; dz += 2) {
                    level.setBlockAndUpdate(centre.offset(dx, 0, dz), Blocks.CHEST.defaultBlockState());
                }
            }
        });
        singleplayer.getConnection().waitForChunksRender();
        context.waitTicks(20);

        configure(context, "BaseFinder", module -> {
            module.settings.setSetting("interval", 5.0);
            module.settings.setSetting("minimumStorage", 6.0);
        });
        toggle(context, "BaseFinder", true);
        context.waitTicks(20);
        String withoutStorage = context.computeOnClient(client ->
                AgalarHackClient.moduleManager.getModule("BaseFinder").getDisplayName());
        if (!"BaseFinder [needs StorageESP]".equals(withoutStorage)) {
            toggle(context, "BaseFinder", false);
            throw new AssertionError("BaseFinder read " + withoutStorage + " with StorageESP off, rather "
                    + "than saying what it is waiting for");
        }

        toggle(context, "StorageESP", true);
        Predicate<Minecraft> found = client ->
                AgalarHackClient.moduleManager.getModule("BaseFinder")
                        instanceof me.mrhakan.agalarhack.module.world.BaseFinder finder
                        && !finder.clusters().isEmpty()
                        && finder.clusters().get(0).size() >= 6;
        boolean reported = settle(context, found, SETTLE_TICKS * 2);
        boolean announced = context.computeOnClient(notice("Likely base:")::test);
        int size = context.computeOnClient(client -> {
            var finder = (me.mrhakan.agalarhack.module.world.BaseFinder)
                    AgalarHackClient.moduleManager.getModule("BaseFinder");
            return finder.clusters().isEmpty() ? 0 : finder.clusters().get(0).size();
        });
        toggle(context, "StorageESP", false);
        toggle(context, "BaseFinder", false);

        if (!reported) {
            throw new AssertionError("BaseFinder found no cluster in nine chests within six blocks of "
                    + "the player, with StorageESP on");
        }
        if (!announced) {
            throw new AssertionError("BaseFinder found a cluster of " + size
                    + " but published no notification about it");
        }
        LOGGER.info("  BaseFinder reported a cluster of {} storage blocks as a likely base", size);
    }

    /**
     * Arrows are put in the air on a course that passes through the player, and the module is asked
     * to see them coming.
     *
     * <p>Several rather than one, spread over a range of distances: the tracked list this reads is
     * filled by a scheduled scan, and a single arrow can be past the player before the scan that
     * would have found it runs. The claim is unaffected — one warning is one warning.
     */
    private void projectileWarning(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        toggle(context, "Notifications", true);
        moveThere(context, singleplayer, sceneBase.offset(0, 0, 300));
        singleplayer.getServer().runOnServer(server -> {
            for (Entity entity : singleplayer.getConnection().getServerPlayer().level().getAllEntities()) {
                if (!(entity instanceof ServerPlayer)) entity.discard();
            }
        });
        context.waitTicks(10);

        toggle(context, "ProjectileWarning", true);
        context.waitTicks(20);
        String alone = context.computeOnClient(client ->
                AgalarHackClient.moduleManager.getModule("ProjectileWarning").getDisplayName());
        if (!"ProjectileWarning [needs ProjectileESP]".equals(alone)) {
            toggle(context, "ProjectileWarning", false);
            throw new AssertionError("ProjectileWarning read " + alone + " with ProjectileESP off, rather "
                    + "than saying what it is waiting for");
        }

        toggle(context, "ProjectileESP", true);
        context.waitTicks(20);
        assertNotYet(context, notice("Incoming"), "an incoming projectile was reported before one existed");

        // Volleys rather than one salvo. An arrow crosses the twenty blocks in about seven ticks,
        // and the list this module reads is filled by a scheduled scan that may not have run in
        // that window; firing again costs a few ticks and removes the race.
        boolean warned = false;
        int tracked = 0;
        for (int volley = 0; volley < 8 && !warned; volley++) {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = singleplayer.getConnection().getServerPlayer();
                ServerLevel level = player.level();
                double eye = player.getEyeY();
                for (int step = 0; step < 3; step++) {
                    Entity arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.COMMAND);
                    if (arrow == null) throw new AssertionError("could not create an arrow");
                    arrow.setPos(player.getX() + 14 + step * 4, eye, player.getZ());
                    // A bow shoots at three blocks per tick. Anything slower falls into the ground
                    // long before it arrives, which is a scenario that proves nothing rather than a
                    // module that missed something.
                    arrow.setDeltaMovement(new Vec3(-3.0, 0.0, 0.0));
                    level.addFreshEntity(arrow);
                }
            });
            warned = settle(context, notice("Incoming"), 20);
            tracked = Math.max(tracked, context.computeOnClient(client ->
                    AgalarHackClient.moduleManager.getModule("ProjectileESP")
                            instanceof me.mrhakan.agalarhack.module.render.ProjectileESP esp
                            ? esp.projectiles().size() : 0));
        }
        toggle(context, "ProjectileWarning", false);
        toggle(context, "ProjectileESP", false);
        singleplayer.getServer().runOnServer(server -> {
            for (Entity entity : singleplayer.getConnection().getServerPlayer().level().getAllEntities()) {
                if (!(entity instanceof ServerPlayer)) entity.discard();
            }
        });

        if (!warned) {
            // Which half broke: nothing tracked is a scene or discovery problem, a tracked list with
            // no warning is this module's.
            throw new AssertionError("ProjectileWarning said nothing about arrows flying straight at "
                    + "the player's head; ProjectileESP had tracked at most " + tracked + " of them");
        }
        LOGGER.info("  ProjectileWarning saw an arrow coming and said so");
    }

    /**
     * A dozen arrows stuck in the ground in front of the camera, which is the one way to hold a
     * projectile still long enough to photograph what the module draws around it.
     *
     * <p>They are spent arrows rather than arrows in flight on purpose: an arrow crossing the frame
     * moves the picture by itself, which is exactly the noise the measurement is trying to exclude.
     */
    private void projectileEsp(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        moveThere(context, singleplayer, sceneBase.offset(0, 0, 330));
        BlockPos cluster = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            ServerLevel level = player.level();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof ServerPlayer)) entity.discard();
            }
            BlockPos centre = player.blockPosition().offset(5, 0, 0);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 2; dx++) {
                    Entity arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.COMMAND);
                    if (arrow == null) throw new AssertionError("could not create an arrow");
                    // A block up with no motion: it drops, sticks, and stays put from then on.
                    arrow.setPos(centre.getX() + dx + 0.5, centre.getY() + 1.0, centre.getZ() + dz + 0.5);
                    arrow.setDeltaMovement(Vec3.ZERO);
                    level.addFreshEntity(arrow);
                }
            }
            return centre;
        });
        singleplayer.getConnection().waitForChunksRender();
        context.waitTicks(40);
        context.getInput().lookAt(cluster);
        context.waitTicks(20);

        int arrows = context.computeOnClient(client -> {
            int seen = 0;
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity instanceof net.minecraft.world.entity.projectile.Projectile) seen++;
            }
            return seen;
        });
        if (arrows == 0) {
            throw new AssertionError("no arrow reached the client, so ProjectileESP has nothing to draw; "
                    + "the scenario is broken, not the module");
        }

        drawsSomething(context, "ProjectileESP", "boxes around the spent arrows", 0.25, 0.75,
                client -> AgalarHackClient.moduleManager.getModule("ProjectileESP")
                        instanceof me.mrhakan.agalarhack.module.render.ProjectileESP esp
                        && !esp.projectiles().isEmpty());
        singleplayer.getServer().runOnServer(server -> {
            for (Entity entity : singleplayer.getConnection().getServerPlayer().level().getAllEntities()) {
                if (!(entity instanceof ServerPlayer)) entity.discard();
            }
        });
    }

    /**
     * Last, because it ends with the player somewhere the world builder chose rather than where the
     * scenario left them.
     *
     * <p>The control is a real wait on a real death screen: sixty ticks with the module off, which
     * is six times its default delay, and the screen is still there. Without that, a test that
     * enabled the module and saw the screen close could be watching the game do it.
     */
    private void autoRespawn(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        Predicate<Minecraft> onDeathScreen =
                client -> client.gui.screen() instanceof net.minecraft.client.gui.screens.DeathScreen;

        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            player.setGameMode(GameType.SURVIVAL);
            player.setHealth(player.getMaxHealth());
        });
        context.waitTicks(20);
        singleplayer.getServer().runOnServer(server ->
                singleplayer.getConnection().getServerPlayer().kill(
                        singleplayer.getConnection().getServerPlayer().level()));

        if (!settle(context, onDeathScreen)) {
            throw new AssertionError("the player did not reach a death screen after being killed in "
                    + "survival; the scenario is broken, not the module");
        }
        context.waitTicks(60);
        if (!context.computeOnClient(onDeathScreen::test)) {
            throw new AssertionError("the death screen closed on its own with AutoRespawn off; "
                    + "the scenario proves nothing in that state");
        }

        toggle(context, "AutoRespawn", true);
        boolean respawned = settle(context, onDeathScreen.negate(), SETTLE_TICKS * 2);
        toggle(context, "AutoRespawn", false);
        singleplayer.getServer().runOnServer(server ->
                singleplayer.getConnection().getServerPlayer().setGameMode(GameType.CREATIVE));

        if (!respawned) {
            throw new AssertionError("AutoRespawn left the player on the death screen for "
                    + (SETTLE_TICKS * 2) + " ticks");
        }
        LOGGER.info("  AutoRespawn cleared a death screen the game left standing");
    }

    /**
     * The cast is real and the bite is not.
     *
     * <p>Casting is asserted end to end: a rod goes in the hotbar, the module is switched on, and a
     * fishing hook appears because the module selected the slot and pressed use. Nothing about that
     * is simulated.
     *
     * <p>The bite is. A server picks its own moment between five and thirty seconds, which is a long
     * time to hold a test open for a result that is a coin toss on a slow runner. What the module
     * actually watches for is the bobber being pulled under — it says so, and it cannot know a fish
     * is there — so the bobber is pulled under on the server instead. That is the cue reproduced,
     * not the module's decision: whether it reels in, how long it waits and whether it casts again
     * are still entirely the module's. Fishing against a real server on a real catch stays on the
     * manual acceptance list.
     */
    private void autoFish(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        moveThere(context, singleplayer, sceneBase.offset(0, 0, 390));
        BlockPos pool = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            ServerLevel level = player.level();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof ServerPlayer)) entity.discard();
            }
            // A wide pool starting three blocks ahead, two deep so a bobber floats rather than
            // resting on the bottom. No wall is built around it: the untouched superflat terrain at
            // the same depth already holds the water in. The first version raised a stone lip and
            // the lip stood at head height between the player and the water, so every cast hit it -
            // which the scenario reported as the bobber never reaching the water, correctly.
            BlockPos centre = player.blockPosition().offset(8, 0, 0);
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    level.setBlockAndUpdate(centre.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
                    for (int dy = -2; dy <= -1; dy++) {
                        level.setBlockAndUpdate(centre.offset(dx, dy, dz), Blocks.WATER.defaultBlockState());
                    }
                }
            }
            return centre;
        });
        singleplayer.getConnection().waitForChunksRender();
        setInventory(singleplayer, slots -> {
            slots.setItem(0, new ItemStack(Items.FISHING_ROD));
            slots.setSelectedSlot(0);
        });
        // At the middle of the pool. It is thirteen blocks across, so any ordinary cast lands in it.
        context.getInput().lookAt(pool);
        context.waitTicks(20);

        Predicate<Minecraft> hooked = client -> client.player.fishing != null && client.player.fishing.isAlive();
        assertNotYet(context, hooked, "a fishing hook was already out before AutoFish ran");

        toggle(context, "AutoFish", true);
        boolean cast = settle(context, hooked);
        if (!cast) {
            toggle(context, "AutoFish", false);
            throw new AssertionError("AutoFish never cast with a rod in the selected hotbar slot");
        }
        // Waiting for the bobber to settle in the water: the detector ignores a hook that is not in it.
        Vec3 start = position(context);
        boolean floating = settle(context, client -> client.player.fishing != null
                && client.player.fishing.isInWater());
        String bobber = context.computeOnClient(client -> client.player.fishing == null ? "gone"
                : "at " + client.player.fishing.position() + " inWater="
                        + client.player.fishing.isInWater() + " onGround="
                        + client.player.fishing.onGround());
        int firstHook = context.computeOnClient(client -> client.player.fishing.getId());

        if (floating) {
            singleplayer.getServer().runOnServer(server -> {
                var hook = singleplayer.getConnection().getServerPlayer().fishing;
                // Well past the default threshold of four hundredths of a block per tick.
                if (hook != null) hook.setDeltaMovement(0.0, -0.4, 0.0);
            });
        }
        boolean reeled = floating && settle(context, client -> client.player.fishing == null
                || client.player.fishing.getId() != firstHook);
        toggle(context, "AutoFish", false);
        singleplayer.getServer().runOnServer(server -> {
            var hook = singleplayer.getConnection().getServerPlayer().fishing;
            if (hook != null) hook.discard();
        });

        if (!floating) {
            throw new AssertionError("the bobber never reached the water, so there was no bite cue to "
                    + "give AutoFish; the scenario is broken, not the module. The hook ended " + bobber
                    + ", with the player at " + start + " and water from x=" + (pool.getX() - 7)
                    + " to x=" + (pool.getX() + 7));
        }
        if (!reeled) {
            throw new AssertionError("AutoFish left the same hook out after the bobber was pulled "
                    + "under, which is the one cue it reels in on");
        }
        LOGGER.info("  AutoFish cast, then reeled in when the bobber went under");
    }

    /**
     * The HUD drawn at a different size, which is the one thing a scale control has to actually do.
     *
     * <p>Measured over the top-left corner rather than the middle band the other render scenarios
     * use, because that is where the branding widget draws and the middle band excludes it. Narrow
     * on purpose: a wider band takes in widgets whose text changes by itself — a frame counter, a
     * ping figure — and their noise is indistinguishable from a size change.
     *
     * <p>Set through {@code ThemeService.preview}, the same call the slider makes, so this covers the
     * path from the stored theme to the layout rather than poking the layout directly.
     */
    private void hudScale(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        moveThere(context, singleplayer, sceneBase.offset(0, 0, 420));
        singleplayer.getServer().runOnServer(server -> {
            for (Entity entity : singleplayer.getConnection().getServerPlayer().level().getAllEntities()) {
                if (!(entity instanceof ServerPlayer)) entity.discard();
            }
        });
        context.runOnClient(client -> client.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF));
        // Up at empty sky. The previous scenario leaves the camera angled down at a pool, and water
        // animates, so the corner being measured had a moving picture behind it.
        context.getInput().lookAt(0.0f, -40.0f);
        context.waitTicks(40);

        final int window = 60;
        setHudScale(context, me.mrhakan.agalarhack.services.HudScale.DEFAULT);
        java.nio.file.Path first = context.takeScreenshot("hud-scale-1");
        context.waitTicks(window);
        java.nio.file.Path second = context.takeScreenshot("hud-scale-2");
        int noise = Frames.changedPixels(first, second, BRAND_TOP, BRAND_BOTTOM, 0.0, BRAND_RIGHT);

        setHudScale(context, 1.6);
        context.waitTicks(window);
        java.nio.file.Path larger = context.takeScreenshot("hud-scale-3");
        int signal = Frames.changedPixels(second, larger, BRAND_TOP, BRAND_BOTTOM, 0.0, BRAND_RIGHT);
        double applied = context.computeOnClient(client -> AgalarHackClient.HUD_LAYOUT.scale());
        setHudScale(context, me.mrhakan.agalarhack.services.HudScale.DEFAULT);
        double restored = context.computeOnClient(client -> AgalarHackClient.HUD_LAYOUT.scale());

        LOGGER.info("    HUD scale pixels: noise={} signal={}", noise, signal);
        if (applied != 1.6) {
            throw new AssertionError("the theme's HUD scale of 1.6 reached the layout as " + applied
                    + ", so nothing below measures a scaled HUD");
        }
        if (restored != me.mrhakan.agalarhack.services.HudScale.DEFAULT) {
            throw new AssertionError("the HUD scale stayed at " + restored + " after being set back");
        }
        assertDrew("HUD scale", "a HUD any different in size", noise, signal);
    }

    /** The corner the branding widget occupies, as a fraction of the frame. */
    private static final double BRAND_TOP = 0.0, BRAND_BOTTOM = 0.06, BRAND_RIGHT = 0.35;

    private static void setHudScale(ClientGameTestContext context, double scale) {
        context.runOnClient(client -> {
            var service = me.mrhakan.agalarhack.services.ClientServices.require(
                    me.mrhakan.agalarhack.services.ThemeService.class);
            var theme = service.copy();
            theme.hudScale = scale;
            // The HUD animates its colours continuously, which showed up as a noise floor of 964
            // changed pixels between any two frames however far apart - a moving picture rather than
            // jitter. The theme's own reduced-motion switch stops it, which is the honest way to
            // hold the scene still: a setting a player has, not a hack for the test.
            theme.reducedMotion = true;
            theme.uiAnimations = false;
            service.preview(theme);
        });
    }

    /**
     * The ClickGUI's category transition, driven by a real click on a real button.
     *
     * <p>Asserted on the stripe's position rather than on pixels: what the transition does is move
     * something from one row to another, and reading where it is says that directly. The control is
     * the theme's own reduced-motion switch — with it on the stripe is at its destination on the
     * first frame, which is the whole promise of that setting.
     *
     * <p>The animation is slowed for the measurement, not shortened: at the default speed it lasts
     * about three and a half ticks, which is too close to the sampling interval to read reliably.
     */
    private void clickGuiTransition(ClientGameTestContext context) {
        int target = 3;
        int travelled = categoryStripeTravel(context, target, true);
        int still = categoryStripeTravel(context, target, false);

        if (travelled <= 0) {
            throw new AssertionError("the ClickGUI's category transition was already finished on the "
                    + "first frame with motion on, so nothing was animated");
        }
        if (still != 0) {
            throw new AssertionError("the ClickGUI transition still had " + still + " to run with the "
                    + "theme's reduced motion on; that switch is supposed to leave nothing animating");
        }
        LOGGER.info("  ClickGUI animated its category change ({} to go a tick in), and not at all "
                + "with reduced motion", travelled);
    }

    /**
     * Opens the ClickGUI, clicks a category, and reports how far the stripe still had to travel one
     * tick later.
     *
     * @return how far the transition still had to run a tick after the click: the stripe's remaining
     *         travel in pixels plus the content veil in hundredths, so one number covers both
     */
    private int categoryStripeTravel(ClientGameTestContext context, int category, boolean motion) {
        context.runOnClient(client -> {
            var service = me.mrhakan.agalarhack.services.ClientServices.require(
                    me.mrhakan.agalarhack.services.ThemeService.class);
            var theme = service.copy();
            theme.uiAnimations = motion;
            theme.reducedMotion = !motion;
            // Quarter speed: long enough that a tick of sampling lands inside the transition.
            theme.animationSpeed = 0.25;
            service.preview(theme);
            client.gui.setScreen(new me.mrhakan.agalarhack.ui.ClickGuiScreen());
        });
        context.waitTicks(10);

        // Where the category's own button is, in window pixels: the screen is drawn at the GUI scale.
        double[] spot = context.computeOnClient(client -> {
            var screen = (me.mrhakan.agalarhack.ui.ClickGuiScreen) client.gui.screen();
            double scale = client.getWindow().getGuiScale();
            return new double[]{50 * scale,
                    (screen.categoryRowY(category) + screen.categoryRowHeight() / 2.0) * scale};
        });
        context.getInput().setCursorPos(spot[0], spot[1]);
        context.waitTicks(2);
        context.getInput().pressMouse(0);
        context.waitTicks(1);

        int remaining = context.computeOnClient(client -> {
            if (!(client.gui.screen() instanceof me.mrhakan.agalarhack.ui.ClickGuiScreen screen)) return -1;
            // The veil covers the same transition from the other end: the content fading up on
            // open, on a category and on a page. Counted in the same units so one number carries
            // both - a hundredth of the veil is a pixel's worth of movement.
            return Math.abs(screen.categoryRowY(category) - screen.stripeY())
                    + (int) Math.round(screen.contentVeil() * 100);
        });
        context.runOnClient(client -> client.gui.setScreen(null));
        context.waitTicks(5);
        if (remaining < 0) {
            throw new AssertionError("clicking the category at " + spot[1] + " did not leave the "
                    + "ClickGUI open, so there was no stripe to measure; the scenario is broken");
        }
        return remaining;
    }

    /**
     * The verdict, and the reason for it.
     *
     * <p>The test is the ratio: the module has to move the picture several times more than the scene
     * moves on its own, measured over the same number of ticks in the same run. The absolute floor
     * only rules out a handful of stray pixels when the scene is perfectly still and the noise is
     * zero, which would otherwise let any difference at all count.
     *
     * <p>A high noise floor is not by itself a failure. It was, at first, and that rejected ItemESP
     * moving 1822 pixels against a drop bobbing through 210 - a margin of nearly nine times. What a
     * high noise floor does mean is that a *failure* needs a different explanation, so it chooses the
     * message: a scene that will not hold still cannot tell a module apart from itself, and saying
     * "it drew nothing" there would be blaming the wrong thing.
     *
     * <p>The one case in this session that this would have let through - cloud drift read as a
     * healthy signal - came from measuring noise over ten ticks and signal over sixty. With equal
     * windows the drift lands in both numbers and the ratio collapses to about one.
     */
    private static void assertDrew(String name, String expected, int noise, int signal) {
        if (signal >= Math.max(DRAWN_PIXELS, noise * 5)) {
            return;
        }
        if (noise > DRAWN_PIXELS) {
            throw new AssertionError(name + " changed " + signal + " pixels, against " + noise
                    + " that change with the module off; the scene will not hold still enough to tell "
                    + "the module apart from it, so this scenario proves nothing either way");
        }
        throw new AssertionError(name + " changed " + signal + " pixels against a noise floor of "
                + noise + "; it drew no " + expected);
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
        drawsSomething(context, name, expected, fromHeight, toHeight, null);
    }

    /**
     * As above, with a guard on whether the module had anything to draw when the frame was taken.
     *
     * <p>Scanning modules fill their result set on a shared budget, and an empty one produces a
     * frame identical to the control. Without this the scenario reports that as "drew nothing",
     * which blames the module for what is really the scene or the schedule. The guard is read
     * rather than waited on, deliberately: waiting would make the signal window longer than the
     * noise window, and unequal windows are how drift gets counted as signal.
     */
    private void drawsSomething(ClientGameTestContext context, String name, String expected,
            double fromHeight, double toHeight, Predicate<Minecraft> hadSomethingToDraw) {
        final int window = 60;
        String slug = name.toLowerCase(java.util.Locale.ROOT);
        java.nio.file.Path first = context.takeScreenshot(slug + "-off-1");
        context.waitTicks(window);
        java.nio.file.Path second = context.takeScreenshot(slug + "-off-2");
        int noise = Frames.changedPixels(first, second, fromHeight, toHeight);

        toggle(context, name, true);
        context.waitTicks(window);
        java.nio.file.Path on = context.takeScreenshot(slug + "-on");
        boolean anything = hadSomethingToDraw == null || context.computeOnClient(hadSomethingToDraw::test);
        toggle(context, name, false);
        int signal = Frames.changedPixels(second, on, fromHeight, toHeight);

        LOGGER.info("    {} pixels: noise={} signal={}", name, noise, signal);
        if (!anything) {
            throw new AssertionError(name + " had found nothing to draw when the frame was taken, so "
                    + "the comparison would be measuring the scene rather than the module; the "
                    + "scenario is broken, not the module");
        }
        assertDrew(name, expected, noise, signal);
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
