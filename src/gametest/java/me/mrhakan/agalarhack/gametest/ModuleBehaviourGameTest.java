Warning: truncated output (original token count: 35209)
Total output lines: 2566

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
        weaponFamilies(context, singleplayer);
            betterChat(context, singleplayer);
            chatFilter(context, singleplayer);
            chatMentions(context, singleplayer);
            chatHighlight(context, singleplayer);
            autoAccept(context, singleplayer);
            performance(context);
            serverInfo(context);
            gamemodeAlerts(context, singleplayer);
            critInfo(context, singleplayer);
            elytraInfo(context, singleplayer);
            totemTracker(context, singleplayer);
            combatHistory(context, singleplayer);
            baseFinder(context, singleplayer);
            projectileWarning(context, singleplayer);
            autoFish(context, singleplayer);
            hudScale(context, singleplayer);
            clickGuiTransition(context);
            freecam(context, singleplayer);
            lookCommand(context, singleplayer);
            baritoneAbsent(context, singleplayer);
            grindPlan(context, singleplayer);
            addonLoaded(context);
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
        // Deliberately disjoi…20209 tokens truncated…urn entity == null ? 0.0 : entity.getPosition(0.0f).distanceTo(entity.position());
        });
        if (interpolationGap > 1.0) {
            throw new AssertionError("the camera renders " + String.format(java.util.Locale.ROOT,
                    "%.2f", interpolationGap) + " blocks from where it actually is, because its "
                    + "previous position was never brought along; the view would swim every frame");
        }

        toggle(context, "Freecam", false);
        context.waitTicks(10);
        boolean restored = context.computeOnClient(client -> client.getCameraEntity() == client.player);

        if (!detached) {
            throw new AssertionError("Freecam did not detach the camera from the player");
        }
        if (travelled < 1.0) {
            throw new AssertionError("the camera moved " + travelled + " blocks while the forward key "
                    + "was held for eighty ticks, so Freecam is not flying it");
        }
        // A little over, because the position is eased towards a target that is itself on the limit.
        if (travelled > radius + 0.5) {
            throw new AssertionError("the camera reached " + travelled + " blocks from the body with "
                    + "the limit set to " + radius);
        }
        if (bodyAfter.distanceTo(body) > 0.5) {
            throw new AssertionError("the player moved " + bodyAfter.distanceTo(body) + " blocks while "
                    + "the camera was detached; Freecam is supposed to leave the body where it is");
        }
        if (!restored) {
            throw new AssertionError("the camera was not handed back to the player when Freecam was "
                    + "switched off, so the client is still looking through the armour stand");
        }
        LOGGER.info("  Freecam strafed right, flew the camera {} blocks, held it inside {}, left the "
                + "body still and gave the camera back",
                String.format(java.util.Locale.ROOT, "%.1f", travelled), radius);
    }

    /** Where the detached camera actually is, which is the only way to check which way it flew. */
    private static Vec3 cameraPosition(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            var camera = client.getCameraEntity();
            return camera == null ? Vec3.ZERO : camera.position();
        });
    }

    /**
     * The rotation service driven by something that is not Aura.
     *
     * <p>That was the open half of the rotation work: one arbitrated, step-limited path, with a
     * single consumer. This drives it from a typed command instead, which is the case the service
     * was built for and had never been exercised by — a caller that asks once and needs the aim held
     * across ticks.
     *
     * <p>The view really moves, which is the point. A rotation the player cannot see but the server
     * can is the thing this client refuses to implement, so there is nothing here to hide.
     */
    private void lookCommand(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        moveThere(context, singleplayer, sceneBase.offset(0, 0, 480));
        context.getInput().lookAt(0.0f, 0.0f);
        context.waitTicks(10);

        float startYaw = context.computeOnClient(client -> client.player.getYRot());
        // Through the mod's own chat dispatch, alias resolution and all, rather than by calling the
        // command object directly.
        context.runOnClient(client -> me.mrhakan.agalarhack.managers.CommandManager.handleChat(
                me.mrhakan.agalarhack.AgalarHackClient.prefix + "look 90 25"));
        boolean turned = settle(context, client ->
                me.mrhakan.agalarhack.services.LookController.arrived(
                        client.player.getYRot(), client.player.getXRot(), 90.0f, 25.0f));
        float endYaw = context.computeOnClient(client -> client.player.getYRot());
        float endPitch = context.computeOnClient(client -> client.player.getXRot());

        if (!turned) {
            throw new AssertionError("the view did not reach 90, 25 within " + SETTLE_TICKS
                    + " ticks of the look command; it ended at " + endYaw + ", " + endPitch
                    + " having started at " + startYaw);
        }

        // Letting go matters as much as turning: a controller that keeps asking owns the player's
        // view for the rest of the session.
        boolean released = settle(context, client ->
                !me.mrhakan.agalarhack.services.ClientServices.require(
                        me.mrhakan.agalarhack.services.LookController.class).active());
        if (!released) {
            throw new AssertionError("the look controller was still holding the view after arriving");
        }

        // And the player can still turn their own head afterwards.
        context.getInput().lookAt(0.0f, 0.0f);
        context.waitTicks(10);
        float afterward = context.computeOnClient(client -> client.player.getYRot());
        if (me.mrhakan.agalarhack.services.LookController.arrived(afterward, 0, 90.0f, 0)) {
            throw new AssertionError("the view snapped back to the commanded heading, so something "
                    + "is still steering it");
        }
        LOGGER.info("  look turned the view from {} to {}, {} and let go of it",
                String.format(java.util.Locale.ROOT, "%.0f", startYaw),
                String.format(java.util.Locale.ROOT, "%.0f", endYaw),
                String.format(java.util.Locale.ROOT, "%.0f", endPitch));
    }

    /**
     * The matched word inside a line, coloured, with the rest of the line untouched.
     *
     * <p>Asserted on the drawn component rather than on pixels, because what matters here is which
     * characters carry the colour. Two things are checked and both can break on their own: the line
     * still reads exactly as it arrived, and the colour is on the keyword and on nothing else. A
     * highlighter that colours the whole line would pass the first check and fail the second.
     */
    private void chatHighlight(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        String keyword = "kumquat";
        String line = "the kumquat is not a plum";
        configure(context, "ChatMentions", module -> {
            module.settings.setSetting("keywords", keyword);
            module.settings.setSetting("ownName", false);
            module.settings.setSetting("sound", false);
            module.settings.setSetting("cooldown", 0.0);
        });
        configure(context, "BetterChat", module -> {
            module.settings.setSetting("highlightMentions", true);
            // Off so the assertion below sees the line and nothing prepended to it.
            module.settings.setSetting("timestamps", false);
            module.settings.setSetting("markMentions", false);
        });
        toggle(context, "ChatMentions", true);
        toggle(context, "BetterChat", true);

        somebodySays(context, singleplayer, "Someone", line);
        context.waitTicks(20);

        String coloured = context.computeOnClient(client -> {
            var drawn = ChatView.find(client, "kumquat is not a plum");
            if (drawn == null) return "<nothing arrived>";
            StringBuilder gold = new StringBuilder();
            drawn.visit((style, text) -> {
                if (style.getColor() != null
                        && style.getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(
                                net.minecraft.ChatFormatting.GOLD))) {
                    gold.append(text);
                }
                return java.util.Optional.empty();
            }, net.minecraft.network.chat.Style.EMPTY);
            return gold.toString();
        });
        String whole = context.computeOnClient(client -> {
            var drawn = ChatView.find(client, "kumquat is not a plum");
            return drawn == null ? "" : drawn.getString();
        });
        toggle(context, "BetterChat", false);
        toggle(context, "ChatMentions", false);

        if (!whole.contains(line)) {
            throw new AssertionError("the line was changed by the highlighting: it reads \"" + whole
                    + "\" rather than containing \"" + line + "\"");
        }
        if (!coloured.equals(keyword)) {
            throw new AssertionError("the gold run reads \"" + coloured + "\" rather than \"" + keyword
                    + "\"; the highlight is on the wrong characters");
        }
        LOGGER.info("  BetterChat coloured \"{}\" inside the line and left the rest of it alone", coloured);
    }

    /**
     * What happens when Baritone is not installed, which is the case this client ships in.
     *
     * <p>The danger the bridge exists to avoid is one line long: sending {@code #goto 100 64 -200}
     * as a chat message puts a player's base coordinates in front of the whole server the moment
     * Baritone is missing or its prefix is off. So the assertion is not only that the player is told
     * something useful — it is that <em>no</em> line beginning with the Baritone prefix ever reached
     * the chat, and that the server never echoed one back.
     *
     * <p>The other half, a Baritone that is present, cannot be tested here: the client under test
     * has no Baritone. That half is covered by the unit tests, which run the bridge's reflection
     * against a stub carrying the documented API names and shapes.
     */
    private void baritoneAbsent(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        moveThere(context, singleplayer, sceneBase.offset(0, 0, 510));
        boolean installed = context.computeOnClient(client ->
                me.mrhakan.agalarhack.services.ClientServices.require(
                        me.mrhakan.agalarhack.services.BaritoneBridge.class).available());
        if (installed) {
            throw new AssertionError("Baritone turned out to be on the test classpath, so this "
                    + "scenario is measuring the wrong half; it asserts the absent behaviour");
        }

        context.runOnClient(client -> me.mrhakan.agalarhack.managers.CommandManager.handleChat(
                me.mrhakan.agalarhack.AgalarHackClient.prefix + "goto 1000 64 -2000"));
        context.waitTicks(20);

        boolean told = context.computeOnClient(client ->
                ChatView.contains(client, "Baritone is not installed"));
        // The coordinates must not appear anywhere except inside the client's own refusal.
        java.util.List<String> leaked = context.computeOnClient(client ->
                ChatView.lines(client).stream()
                        .filter(line -> line.contains("#goto") || line.contains("1000")
                                && !line.contains("Baritone is not installed"))
                        .toList());

        if (!told) {
            throw new AssertionError("the goto command said nothing about Baritone being missing; "
                    + "a command that silently does nothing is how a player ends up typing it again");
        }
        if (!leaked.isEmpty()) {
            throw new AssertionError("something reached the chat that should not have: " + leaked
                    + "; this is exactly the coordinate leak the bridge exists to prevent");
        }
        LOGGER.info("  goto refused without Baritone and put nothing in chat");
    }

    /**
     * The grind planner against a real inventory, which is the half of it that can be wrong quietly.
     *
     * <p>The arithmetic is unit tested; what this adds is the mapping from a real held item onto the
     * name the book counts. Three oak logs have to cover the one log a stone pickaxe needs - if the
     * mapping missed that they are "log", the plan would send the player to chop wood they are
     * already carrying, and every unit test would still pass.
     */
    private void grindPlan(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        setInventory(singleplayer, slots -> {
            slots.setItem(0, new ItemStack(Items.OAK_LOG, 3));
            slots.setSelectedSlot(0);
        });
        context.waitTicks(10);
        context.runOnClient(client -> me.mrhakan.agalarhack.managers.CommandManager.handleChat(
                me.mrhakan.agalarhack.AgalarHackClient.prefix + "grind stone_pickaxe"));
        context.waitTicks(20);

        java.util.List<String> lines = context.computeOnClient(ChatView::lines);
        boolean asksForStone = lines.stream().anyMatch(line -> line.contains("gather 3 cobblestone"));
        boolean asksForWood = lines.stream().anyMatch(line -> line.contains("gather")
                && line.contains("log"));
        boolean saysItOnlyPlans = lines.stream().anyMatch(line -> line.contains("Planning only"));

        if (!asksForStone) {
            throw new AssertionError("the plan did not ask for the three cobblestone a stone pickaxe "
                    + "needs; chat was " + lines);
        }
        if (asksForWood) {
            throw new AssertionError("the plan asked the player to gather wood while they were "
                    + "holding three oak logs, so a real item id is not being counted under the "
                    + "book's name for it; chat was " + lines);
        }
        if (!saysItOnlyPlans) {
            throw new AssertionError("the command did not say that it only plans. A command that "
                    + "looks like it started a grind and did nothing is worse than one that refuses");
        }
        LOGGER.info("  grind planned a stone pickaxe around the wood already carried");

        context.runOnClient(client -> me.mrhakan.agalarhack.managers.CommandManager.handleChat(
                me.mrhakan.agalarhack.AgalarHackClient.prefix + "grind run cobblestone 1"));
        context.waitTicks(10);
        boolean refusedWithoutBaritone = context.computeOnClient(client ->
                ChatView.contains(client, "AutoGrind needs Baritone installed"));
        if (!refusedWithoutBaritone) {
            throw new AssertionError("AutoGrind started a raw resource run without Baritone or "
                    + "failed to explain that no mining backend is installed");
        }
        LOGGER.info("  AutoGrind refused execution cleanly without Baritone");
    }

    /**
     * An addon that the Fabric loader actually loaded, registering a module and a command.
     *
     * <p>{@link TestAddon} declares itself under the {@code agalarhack} entrypoint in the game test
     * mod's own {@code fabric.mod.json} — the same way a third-party addon would. So this exercises
     * the whole path at once: the loader finding it, the metadata coming from its own mod file, the
     * context it was handed, and both registrations arriving where a player would see them.
     *
     * <p>Checked against the loader's record as well as the client's own registries, because those
     * are the two things that can disagree: an addon whose module registered but was not recorded,
     * or recorded but not registered, is broken either way.
     */
    private void addonLoaded(ClientGameTestContext context) {
        if (!TestAddon.ran) {
            throw new AssertionError("the game test addon was never called, so the agalarhack "
                    + "entrypoint is not being read - nothing below this tests the API, it tests "
                    + "whether the entrypoint key matches");
        }

        boolean moduleThere = context.computeOnClient(client ->
                AgalarHackClient.moduleManager.getModule(TestAddon.MODULE) != null);
        boolean commandThere = context.computeOnClient(client ->
                me.mrhakan.agalarhack.managers.CommandManager.getCommand(TestAddon.COMMAND) != null);
        var record = context.computeOnClient(client ->
                me.mrhakan.agalarhack.services.ClientServices.require(
                        me.mrhakan.agalarhack.services.AddonLoader.class).loaded());

        if (!moduleThere) {
            throw new AssertionError("the addon's module is not in the catalogue, so addModule does "
                    + "not reach the module manager");
        }
        if (!commandThere) {
            throw new AssertionError("the addon's command is not registered, so addCommand does not "
                    + "reach the command manager - most likely it was wiped by the clear() at the "
                    + "start of CommandManager.init(), which is why addons load after it");
        }
        var mine = record.stream().filter(entry -> entry.id().equals(TestAddon.seenId)).findFirst();
        if (mine.isEmpty()) {
            throw new AssertionError("the loader kept no record of the addon it just ran; .addons "
                    + "would not list it and a failure would be invisible. Recorded: " + record);
        }
        if (!mine.get().ok() || mine.get().modules() != 1 || mine.get().commands() != 1) {
            throw new AssertionError("the loader recorded " + mine.get() + ", which does not match "
                    + "the one module and one command the addon registered");
        }
        // The identity handed to the addon has to be its own, not the client's.
        if (TestAddon.seenId == null || TestAddon.seenId.isBlank()
                || TestAddon.seenId.equals("agalarhack")) {
            throw new AssertionError("the addon was told its id is \"" + TestAddon.seenId
                    + "\"; it should be its own mod id, not the client's");
        }
        // The other half of the load order, and the half that would fail silently. registerSettings
        // is called from nowhere but applyValues, inside loadModules, so a module registered after
        // it has no settings at all - no keybind, no HUD toggle, nothing saved between sessions -
        // and nothing about that throws. If this ever fires, addons are loading too late.
        boolean settingsApplied = context.computeOnClient(client -> {
            var module = AgalarHackClient.moduleManager.getModule(TestAddon.MODULE);
            return module != null && module.settings.getSetting("keybind") != null;
        });
        if (!settingsApplied) {
            throw new AssertionError("the addon's module has no settings, so it was registered "
                    + "after loadModules() and will never keep a keybind or anything else across "
                    + "restarts");
        }
        LOGGER.info("  addon {} loaded through the Fabric entrypoint and registered a module and a "
                + "command", TestAddon.seenName);
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
            clearEntities(level);
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
            clearEntities(level);

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

    /**
     * Empties the level of everything except the player.
     *
     * <p>Snapshotted before anything is discarded. {@code getAllEntities()} is a live view, and
     * removing from it while iterating hands back a null - which is exactly how this failed: with a
     * handful of entities the iteration was short enough never to hit it, and once the scenarios
     * before it left arrows, mobs and a fishing bobber lying around, it did. The same loop had been
     * copied fourteen times, so it is one method now.
     */
    private static void clearEntities(ServerLevel level) {
        java.util.List<Entity> present = new java.util.ArrayList<>();
        level.getAllEntities().forEach(present::add);
        for (Entity entity : present) {
            if (entity != null && !(entity instanceof ServerPlayer)) entity.discard();
        }
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
