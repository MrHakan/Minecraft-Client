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
            ServerPlayer player = singleplayerµ¨¥zºè¯
â¶)à²Ö§uªİ¢ëiºĞk¢G§¦*^m«ëŒ+Š×®º+º$zzb¥âævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚“°¢6W'fW$ÆWfVÂÆWfVÂÒÆ–W"æÆWfVÂ‚“°¢f÷"†–çBG‚Ò#²G‚ÃÒc²G‚²²’°¢f÷"†–çBG¢ÒÓ#²G¢ÃÒ#²G¢²²’°¢ÆWfVÂç6WD&Æö6´æEWFFR‡7FæBæöfg6WB†G‚ÂÕ•EôDUD‚ÒÂG¢’Â&Æö6·2å5DôäRæFVfVÇD&Æö6µ7FFR‚’“°¢f÷"†–çBG’ÒÓ²G’ãÒÕ•EôDUDƒ²G’ÒÒ’°¢ÆWfVÂç6WD&Æö6´æEWFFR‡7FæBæöfg6WB†G‚ÂG’ÂG¢’Â&Æö6·2ä•"æFVfVÇD&Æö6µ7FFR‚’“°¢Ğ¢Ğ¢Ğ¢&WGW&â7FæC°¢Ò“°¢6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’çv—Df÷$6‡Væ·5&VæFW"‚“°¢6öçFW‡Bçv—EF–6·2ƒ#“°¢&WGW&âGVs°¢Ğ ¢ò¢¢†÷rF†RÆ–W"w2†V–v‡B6†ævVBGW&–æröæRvÆ²BF†R—BÂ&VÆF—fRFòv†W&RF†W’7F'FVBâ¢ğ¢&—fFR&V6÷&BvÆ²†F÷V&ÆRG&÷ÂF÷V&ÆR&—6R’²Ğ ¢ò¢ ¢¢WG2F†RÆ–W"&6²öâF†RÆVFvRæBvÆ·2F†VÒ–çFòF†R—Bà¢ ¢¢ÇåF†R†V–v‡B—26×ÆVBF‡&÷Vv†÷WB&F†W"F†âöæÇ’BF†RVæBÂ&V6W6R§V×æBfÆÂ&P¢¢FöÆB'B'’v†B†VæVB–â&WGvVVã¢Æ–W"v†ò§V×2F†RvæBöæRv†òæWfW"Ö÷fV@¢¢&÷F‚f–æ—6‚ÆWfVÂv—F‚v†W&RF†W’7F'FVBà¢¢ğ¢&—fFR7FF–2vÆ²vÆ´öfdæE&W÷'B„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂFW7E6–ævÆWÆ–W$6öçFW‡B6–ævÆWÆ–W"À¢&Æö6µ÷2ÆVFvR’°¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óâ°¢6W'fW%Æ–W"Æ–W"Ò6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚“°¢Æ–W"ç6WDvÖTÖöFR„vÖUG—Rå5U%d•dÂ“°¢Æ–W"çFVÆW÷'EFò†ÆVFvRævWE‚‚’²ãRÂÆVFvRævWE’‚’ÂÆVFvRævWE¢‚’²ãR“°¢òòV6‚vÆ²—2—G2÷vâW‡W&–ÖVçC¢Æ–W"6''––ærFÖvR÷"ÖöÖVçGVÒg&öÒF†RÆ7@¢òòöæR—2F–ffW&VçBÆ–W"ÂæBF‡&VRvÆ·2–â&÷rv÷VÆBWfVçGVÆÇ’¶–ÆÂF†VÒà¢Æ–W"ç6WD†VÇF‚‡Æ–W"ævWDÖ„†VÇF‚‚’“°¢Æ–W"ç6WDFVÇFÖ÷fVÖVçB…fV32å¤U$ò“°¢Æ–W"æfÆÄF—7Fæ6RÒ°¢Ò“°¢6öçFW‡Bçv—EF–6·2ƒ#“°¢6öçFW‡BævWD–çWB‚’æÆöö´B†ÆVFvRæöfg6WBƒBÂÂ’“°¢6öçFW‡Bçv—EF–6·2ƒR“° ¢F÷V&ÆR7F'E’Ò÷6—F–öâ†6öçFW‡B’ç“°¢F÷V&ÆRÆ÷vW7BÒ7F'E“°¢F÷V&ÆR†–v†W7BÒ7F'E“°¢6öçFW‡BævWD–çWB‚’æ†öÆD¶W’†÷F–öç2Óâ÷F–öç2æ¶W•W“°¢f÷"†–çBF–6²Ò²F–6²ÂS²F–6²³Ò"’°¢6öçFW‡Bçv—EF–6·2ƒ"“°¢F÷V&ÆR’Ò÷6—F–öâ†6öçFW‡B’ç“°¢Æ÷vW7BÒÖF‚æÖ–â†Æ÷vW7BÂ’“°¢†–v†W7BÒÖF‚æÖ‚††–v†W7BÂ’“°¢Ğ¢6öçFW‡BævWD–çWB‚’ç&VÆV6T¶W’†÷F–öç2Óâ÷F–öç2æ¶W•W“°¢6öçFW‡Bçv—EF–6·2ƒ“°¢òòÆövvVBf÷"WfW'’vÆ³¢v†VâöæRöbF†W6R66Væ&–÷2f–Ç2ÂF†RF‡&VRvÆ·2rçVÖ&W'26–FR'¢òò6–FR&RF†RF–ffW&Væ6R&WGvVVâF–væ÷6—2æBwVW72à¢ÄôttU"æ–æfò‚"vÆ²g&öÒ“×·ÒG&÷×·Ò&—6S×·Òöäw&÷VæC×·Ò"À¢7G&–æræf÷&ÖB‚"Rã&b"Â7F'E’’Â7G&–æræf÷&ÖB‚"Rã&b"ÂÆ÷vW7BÒ7F'E’’À¢7G&–æræf÷&ÖB‚"Rã&b"Â†–v†W7BÒ7F'E’’À¢6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓâ6Æ–VçBçÆ–W"æöäw&÷VæB‚’’“° ¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óà¢6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚’ç6WDvÖTÖöFR„vÖUG—Rä5$TD•dR’“°¢&WGW&âæWrvÆ²†Æ÷vW7BÒ7F'E’Â†–v†W7BÒ7F'E’“°¢Ğ ¢ò¢¢7&VF—fRfÆ–v‡BÆVfW2F†RÆ–W"G&–gF–æs²6WfW&ÂÖöGVÆW2öæÇ’7Bv—F‚&÷F‚fVWBF÷vââ¢ğ¢&—fFR7FF–2fö–B6WGFÆTöäw&÷VæB„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂFW7E6–ævÆWÆ–W$6öçFW‡B6–ævÆWÆ–W"’°¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óà¢6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚’ç6WDvÖTÖöFR„vÖUG—Rä5$TD•dR’“°¢6öçFW‡Bçv—EF–6·2ƒ“°¢Ğ ¢&—fFR7FF–2fV32÷6—F–öâ„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡B’°¢&WGW&â6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓâ6Æ–VçBçÆ–W"ç÷6—F–öâ‚’“°¢Ğ ¢ò¢¢Æ÷r†÷F&"7F6²v—F‚gVÆÂöæR&V†–æB—B–âF†R–çfVçF÷'’6†÷VÆB&RF÷VBWâ¢ğ¢&—fFRfö–BWFõ&Vf–ÆÂ„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂFW7E6–ævÆWÆ–W$6öçFW‡B6–ævÆWÆ–W"’°¢f–æÂ–çB†÷F&%6Æ÷BÒ°¢f–æÂ–çB7F÷&vU6Æ÷BÒ#°¢6WD–çfVçF÷'’‡6–ævÆWÆ–W"Â6Æ÷G2Óâ°¢6Æ÷G2ç6WD—FVÒ††÷F&%6Æ÷BÂæWr—FVÕ7F6²„—FV×2ä4ô$$ÄU5DôäRÂB’“°¢6Æ÷G2ç6WD—FVÒ‡7F÷&vU6Æ÷BÂæWr—FVÕ7F6²„—FV×2ä4ô$$ÄU5DôäRÂcB’“°¢Ò“°¢6öçFW‡Bçv—EF–6·2ƒ“° ¢&VF–6FSÄÖ–æV7&gCâF÷VEWĞ¢6Æ–VçBÓâ6Æ–VçBçÆ–W"ævWD–çfVçF÷'’‚’ævWD—FVÒ††÷F&%6Æ÷B’ævWD6÷VçB‚’âC°¢76W'Dæ÷E–WB†6öçFW‡BÂF÷VEWÂ'F†R†÷F&"7F6²v2Ç&VG’&÷fRF†R&Vf–ÆÂF‡&W6†öÆB"“° ¢FövvÆR†6öçFW‡BÂ$WFõ&Vf–ÆÂ"ÂG'VR“°¢&ööÆVâ&Vf–ÆÆVBÒ6WGFÆR†6öçFW‡BÂF÷VEWÂ4UEDÄUõD”4µ2¢"“°¢FövvÆR†6öçFW‡BÂ$WFõ&Vf–ÆÂ"ÂfÇ6R“°¢–b‚&Vf–ÆÆVB’°¢F‡&÷ræWr76W'F–öäW'&÷"‚$WFõ&Vf–ÆÂÆVgBBÖ—FVÒ†÷F&"7F6²ÆöæRv—F‚gVÆÂ7F6² ¢²&öbF†R6ÖR—FVÒ–âF†R–çfVçF÷'’"“°¢Ğ¢ÄôttU"æ–æfò‚"WFõ&Vf–ÆÂF÷VBWÆ÷r†÷F&"7F6²"“°¢Ğ ¢ò¢ ¢¢F†RöæRWFöÖF–öâ†W&RF†BFW7G&÷—2&÷W'G’Â6òF†R66Væ&–ò6&W22×V6‚&÷WBv†@¢¢7W'f—fW22&÷WBv†BvöW2ââVæÆ—7FVB—FVÒ–âF†RæW‡B6Æ÷B×W7B7F–ÆÂ&RF†W&RgFW'v&G2à¢¢ğ¢&—fFRfö–B–çfVçF÷'”6ÆVæW"„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂFW7E6–ævÆWÆ–W$6öçFW‡B6–ævÆWÆ–W"’°¢f–æÂ–çB§Væµ6Æ÷BÒ#°¢f–æÂ–çB¶VW6Æ÷BÒ#°¢6WD–çfVçF÷'’‡6–ævÆWÆ–W"Â6Æ÷G2Óâ°¢6Æ÷G2ç6WD—FVÒ†§Væµ6Æ÷BÂæWr—FVÕ7F6²„—FV×2å$õEDTåôdÄU4‚Â‚’“°¢6Æ÷G2ç6WD—FVÒ†¶VW6Æ÷BÂæWr—FVÕ7F6²„—FV×2äD”ÔôäBÂ‚’“°¢Ò“°¢6öçFW‡Bçv—EF–6·2ƒ“° ¢&VF–6FSÄÖ–æV7&gCâ§Væ´vöæRĞ¢6Æ–VçBÓâ6Æ–VçBçÆ–W"ævWD–çfVçF÷'’‚’ævWD—FVÒ†§Væµ6Æ÷B’æ—2„—FV×2å$õEDTåôdÄU4‚“°¢76W'Dæ÷E–WB†6öçFW‡BÂ§Væ´vöæRÂ'F†R§Væ²6Æ÷BF–Bæ÷B6öçF–âF†R§Væ²F†R66Væ&–òÆ6VB"“° ¢FövvÆR†6öçFW‡BÂ$–çfVçF÷'”6ÆVæW""ÂG'VR“°¢&ööÆVâG&÷VBÒ6WGFÆR†6öçFW‡BÂ§Væ´vöæRÂ4UEDÄUõD”4µ2¢"“°¢FövvÆR†6öçFW‡BÂ$–çfVçF÷'”6ÆVæW""ÂfÇ6R“°¢–b‚G&÷VB’°¢F‡&÷ræWr76W'F–öäW'&÷"‚$–çfVçF÷'”6ÆVæW"F–Bæ÷BG&÷&÷GFVâfÆW6‚Âv†–6‚—2–â—G2 ¢²&FVfVÇB§Væ²Æ—7B"“°¢Ğ ¢&ööÆVâ¶WBÒ6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓà¢6Æ–VçBçÆ–W"ævWD–çfVçF÷'’‚’ævWD—FVÒ†¶VW6Æ÷B’æ—2„—FV×2äD”ÔôäB’“°¢–b‚¶WB’°¢F‡&÷ræWr76W'F–öäW'&÷"‚$–çfVçF÷'”6ÆVæW"G&÷VBF–ÖöæBÂv†–6‚—2æ÷B–âç’§Væ² ¢²&Æ—7C²F†—2ÖöGVÆR×W7BæWfW"&VÖ÷fR6öÖWF†–æræö&öG’Æ—7FVB"“°¢Ğ¢ÄôttU"æ–æfò‚"–çfVçF÷'”6ÆVæW"G&÷VBF†RÆ—7FVB§Væ²æBÆVgBF†RF–ÖöæG2ÆöæR"“°¢Ğ ¢ò¢ ¢¢v—F‚vVöâ–âF†R†÷F&"æB6öÖWF†–ærFò†—BÂF†R6VÆV7FVB6Æ÷B6†÷VÆB&V6öÖRF†RvVöâà¢ ¢¢ÇåF†RF&vWB—2â&Ö÷W"7FæB&F†W"F†âF†R66VæRw2¦öÖ&–Râ—B—2Æ—f–ærVçF—G’Âv†–6€¢¢—2ÆÂF†RÖöGVÆR6·2f÷"ÂæB—BFöW2æ÷BvÆ²v’ÒF&vWBF†BÖ÷fW2GW&ç2'F†RÖöGVÆP¢¢F–Bæ÷B7v—F6‚"æB'F†R7&÷76†—"Ö—76VB"–çFòF†R6ÖRf–ÇW&Rà¢¢ğ¢&—fFRfö–BWFõvVöâ„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂFW7E6–ævÆWÆ–W$6öçFW‡B6–ævÆWÆ–W"’°¢f–æÂ–çB7v÷&E6Æ÷BÒ3°¢6WD–çfVçF÷'’‡6–ævÆWÆ–W"Â6Æ÷G2Óâ°¢6Æ÷G2ç6WD—FVÒƒÂæWr—FVÕ7F6²„—FV×2ä4ô$$ÄU5DôäRÂb’“°¢6Æ÷G2ç6WD—FVÒ‡7v÷&E6Æ÷BÂæWr—FVÕ7F6²„—FV×2äD”ÔôäEõ5tõ$B’“°¢6Æ÷G2ç6WE6VÆV7FVE6Æ÷Bƒ“°¢Ò“° ¢òòv’g&öÒF†R—BÂöâw&÷VæBF†RV&Æ–W"66Væ&–÷2F–Bæ÷BF–r÷WBâ7væ–ærF†R7Fæ@¢òòv†W&RF†RÆ–W"†VæVBFò&RÆVgB—B7FæF–ær–âF†R—BÂF‡&VR&Æö6·2&VÆ÷rF†P¢òò7&÷76†—"Âv†–6‚F†RwV&B&VÆ÷r6Vv‡B'WBv†–6‚—2F†R66Væ&–òw2fVÇBFòfö–Bà¢&Æö6µ÷27FæBÒ6–ævÆWÆ–W"ævWE6W'fW"‚’æ6ö×WFTöå6W'fW"‡6W'fW"Óâ°¢6W'fW%Æ–W"Æ–W"Ò6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚“°¢&Æö6µ÷2fö÷F–ærÒÆVFvRæöfg6WB‚ÓÂÂ“°¢Æ–W"çFVÆW÷'EFò†fö÷F–ærævWE‚‚’²ãRÂfö÷F–ærævWE’‚’Âfö÷F–ærævWE¢‚’²ãR“°¢&Æö6µ÷27÷BÒfö÷F–æræöfg6WBƒ"ÂÂ“°¢–b„VçF—G•G—W2ä$Ôõ%õ5DäBç7vâ‡Æ–W"æÆWfVÂ‚’Â7÷BÂVçF—G•7vå&V6öâä4ôÔÔäB’ÓÒçVÆÂ’°¢F‡&÷ræWr76W'F–öäW'&÷"‚&6÷VÆBæ÷B7vâF†R&Ö÷W"7FæBB"²7÷B“°¢Ğ¢&WGW&â7÷C°¢Ò“°¢6öçFW‡Bçv—EF–6·2ƒ#“°¢òò—G2WW"†ÆbÂæ÷B—G2fVWC¢&Æö6²Ö6VçG&R–ÒBF†RÆ÷vW"&Æö6²Æöö·2&VÆ÷rF†R&öG’à¢6öçFW‡BævWD–çWB‚’æÆöö´B‡7FæBæ&÷fR‚’“°¢6öçFW‡Bçv—EF–6·2ƒ“° ¢òò–Ö–ær—2F†—266Væ&–òw26WGWÂæ÷B—G26Æ–Òâ–bF†R7&÷76†—"—2æ÷BöâF†R7FæBF†P¢òòÖöGVÆR—26÷'&V7BFòFòæ÷F†–ærÂæB&W÷'F–ærF†B2ÖöGVÆRf–ÇW&Rv÷VÆB&RÆ–Rà¢&ööÆVâ–ÖVBÒ6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓà¢6Æ–VçBæ†—E&W7VÇB–ç7Fæ6VöbæWBæÖ–æV7&gBçv÷&ÆBç‡—2äVçF—G”†—E&W7VÇB“°¢–b‚–ÖVB’°¢F‡&÷ræWr76W'F–öäW'&÷"‚'F†R7&÷76†—"—2æ÷BöâF†R&Ö÷W"7FæBÂ6òWFõvVöâ†2 ¢²&æ÷F†–ærFò&V7BFó²F†R66Væ&–ò—2'&ö¶VâÂæ÷BF†RÖöGVÆR"“°¢Ğ ¢&VF–6FSÄÖ–æV7&gCâ†öÆF–æu7v÷&BĞ¢6Æ–VçBÓâ6Æ–VçBçÆ–W"ævWD–çfVçF÷'’‚’ævWE6VÆV7FVE6Æ÷B‚’ÓÒ7v÷&E6Æ÷C°¢76W'Dæ÷E–WB†6öçFW‡BÂ†öÆF–æu7v÷&BÂ'F†R7v÷&B6Æ÷Bv2Ç&VG’6VÆV7FVB&Vf÷&RWFõvVöâ&â"“° ¢FövvÆR†6öçFW‡BÂ$WFõvVöâ"ÂG'VR“°¢&ööÆVâ7v—F6†VBÒ6WGFÆR†6öçFW‡BÂ†öÆF–æu7v÷&B“°¢FövvÆR†6öçFW‡BÂ$WFõvVöâ"ÂfÇ6R“°¢–b‚7v—F6†VB’°¢F‡&÷ræWr76W'F–öäW'&÷"‚$WFõvVöâF–Bæ÷B6VÆV7BF†RF–ÖöæB7v÷&Bv—F‚Æ—f–ær ¢²'F&vWBVæFW"F†R7&÷76†—""“°¢Ğ¢ÄôttU"æ–æfò‚"WFõvVöâ6VÆV7FVBF†R7v÷&Bf÷"F†RF&vWBVæFW"F†R7&÷76†—""“°¢Ğ ¢ò¢¢6VæG2Æ–æRg&öÒF†R6W'fW"6ò—B'&—fW2F†Rv’&VÂöæRFöW2ÂæBv—G2f÷"—Bâ¢ğ¢&—fFR7FF–2fö–B6’„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂFW7E6–ævÆWÆ–W$6öçFW‡B6–ævÆWÆ–W"Â7G&–ærFW‡B’°¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óà¢6W'fW"ævWEÆ–W$Æ—7B‚’æ'&öF67E7—7FVÔÖW76vR„6ö×öæVçBæÆ—FW&Â‡FW‡B’ÂfÇ6R’“°¢6öçFW‡Bçv—EF–6·2ƒ“°¢Ğ ¢ò¢¢F–ÖW7F×—2&Vf—‚öâF†RÆ–æRÂ6òF†RÆ–æR†2Fò&R&VB&6²Fò6VR—Bâ¢ğ¢&—fFRfö–B&WGFW$6†B„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂFW7E6–ævÆWÆ–W$6öçFW‡B6–ævÆWÆ–W"’°¢6öæf–wW&R†6öçFW‡BÂ$&WGFW$6†B"ÂÖöGVÆRÓâ°¢ÖöGVÆRç6WGF–æw2ç6WE6WGF–ær‚'F–ÖW7F×2"ÂG'VR“°¢ÖöGVÆRç6WGF–æw2ç6WE6WGF–ær‚'6V6öæG2"ÂG'VR“°¢Ò“° ¢7G&–ærÆ–âÒ&&WGFW&6†B6öçG&öÂÆ–æR#°¢6’†6öçFW‡BÂ6–ævÆWÆ–W"ÂÆ–â“°¢&ööÆVâ7F×VEv†–ÆTöfbÒ6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓà¢6†Ef–WræÆ–æW2†6Æ–VçB’ç7G&VÒ‚’æç”ÖF6‚†Æ–æRÓâÆ–æRæ6öçF–ç2‡Æ–â’bbÆ–æRæÖF6†W2‚%åÅÅµÅÆEÅÆC¢â¢"’’“°¢–b‡7F×VEv†–ÆTöfb’°¢F‡&÷ræWr76W'F–öäW'&÷"‚&6†BÆ–æRv2Ç&VG’F–ÖW7F×VB&Vf÷&R&WGFW$6†Bv2öâ"“°¢Ğ ¢FövvÆR†6öçFW‡BÂ$&WGFW$6†B"ÂG'VR“°¢7G&–ær7F×VBÒ&&WGFW&6†B7F×VBÆ–æR#°¢6’†6öçFW‡BÂ6–ævÆWÆ–W"Â7F×VB“°¢FövvÆR†6öçFW‡BÂ$&WGFW$6†B"ÂfÇ6R“° ¢7G&–ærÆ–æRÒ6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓâ6†Ef–WræÆ–æW2†6Æ–VçB’ç7G&VÒ‚¢æf–ÇFW"‡FW‡BÓâFW‡Bæ6öçF–ç2‡7F×VB’’æf–æDf—'7B‚’æ÷$VÇ6R‚""’“°¢òò„ƒ¦ÖÓ§72–â'&6¶WG2Â†VBöbF†R6W'fW"w2÷vâFW‡Bà¢–b‚Æ–æRæÖF6†W2‚%åÅÅµÅÆEÅÆC¥ÅÆEÅÆC¥ÅÆEÅÆEÅÅÒâ¢"²¦fçWF–Âç&VvW‚åGFW&âçV÷FR‡7F×VB’²"â¢"’’°¢F‡&÷ræWr76W'F–öäW'&÷"‚$&WGFW$6†BF–Bæ÷BF–ÖW7F×F†RÆ–æS²6†B6†÷w3¢"²Æ–æR“°¢Ğ¢ÄôttU"æ–æfò‚"&WGFW$6†BF–ÖW7F×VBâ'&—f–ærÆ–æR"“°¢Ğ ¢ò¢ ¢¢F†Rf–ÇFW"†–FW2Æ—7FVB‡&6RæBæ÷F†–ærVÇ6RâF†R6V6öæB†Æb—2F†R†ÆbF†BÖGFW'3 ¢¢f–ÇFW"F†B7vÆÆ÷w2WfW'—F†–ærv÷VÆB72â76W'F–öâF†BöæÇ’6†V6·2F†RÆ—7FVBÆ–æRà¢¢ğ¢&—fFRfö–B6†Df–ÇFW"„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂFW7E6–ævÆWÆ–W$6öçFW‡B6–ævÆWÆ–W"’°¢òòFVÆ–&W&FVÇ’F—6¦ö–çC¢æöæRöbF†W6RF‡&VR—27V'7G&–æröbæ÷F†W"âF†Rf—'7BG&gBW6V@¢òò6öçG&öÂÆ–æRF†B6öçF–æVBF†R†–FFVâöæRÂ6òF†RÆV²6†V6²f÷VæBF†R6öçG&öÂæ@¢òò&W÷'FVBF†RÖöGVÆR†Bf–ÆVBFò†–FRç—F†–ærà¢7G&–ærÆ—7FVBÒ'V–FçVæ2#°¢7G&–ær6öçG&öÂÒ&6†Ff–ÇFW"6öçG&öÂ6'&–W2V–FçVæ2v†–ÆRöfb#°¢7G&–ær†–FFVâÒ&6†Ff–ÇFW"ÆFW"ÖW76vRÇ6ò6''––ærV–FçVæ2#°¢7G&–ær¶WBÒ&6†Ff–ÇFW"–ææö6VçBÆ–æR#°¢6öæf–wW&R†6öçFW‡BÂ$6†Df–ÇFW""ÂÖöGVÆRÓâ°¢ÖöGVÆRç6WGF–æw2ç6WE6WGF–ær‚&†–FR"ÂÆ—7FVB“°¢òòF†R66Væ&–ò7V·2F‡&÷Vv‚F†R6W'fW"6öç6öÆRÂv†–6‚'&—fW22vÖRÖW76vS²F†P¢òòÖöGVÆRÆVfW2F†÷6RÆöæR'’FVfVÇBà¢ÖöGVÆRç6WGF–æw2ç6WE6WGF–ær‚&vÖTÖW76vW2"ÂG'VR“°¢Ò“° ¢òò'6Væ6R—2öæÇ’Wf–FVæ6R–b&W6Væ6Rv2÷76–&ÆRâv—F†÷WBF†—2Â'&öF67BF†BæWfW ¢òò'&—fVBBÆÂv÷VÆB&VBW†7FÇ’Æ–¶RÆ–æRF†Rf–ÇFW"†–Bà¢6’†6öçFW‡BÂ6–ævÆWÆ–W"Â6öçG&öÂ“°¢&ööÆVâ'&—fW2Ò6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓâ6†Ef–Wræ6öçF–ç2†6Æ–VçBÂ6öçG&öÂ’“°¢–b‚'&—fW2’°¢F‡&÷ræWr76W'F–öäW'&÷"‚&6W'fW"Æ–æRFöW2æ÷B&V6‚6†BBÆÂÂ6ò†–F–æröæR&÷fW2 ¢²&æ÷F†–æs²F†R66Væ&–ò—2'&ö¶VâÂæ÷BF†RÖöGVÆR"“°¢Ğ ¢FövvÆR†6öçFW‡BÂ$6†Df–ÇFW""ÂG'VR“°¢6’†6öçFW‡BÂ6–ævÆWÆ–W"Â†–FFVâ“°¢6’†6öçFW‡BÂ6–ævÆWÆ–W"Â¶WB“°¢FövvÆR†6öçFW‡BÂ$6†Df–ÇFW""ÂfÇ6R“° ¢&ööÆVâÆV¶VBÒ6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓâ6†Ef–Wræ6öçF–ç2†6Æ–VçBÂ†–FFVâ’“°¢–b†ÆV¶VB’°¢F‡&÷ræWr76W'F–öäW'&÷"‚$6†Df–ÇFW"F–Bæ÷B†–FRÆ–æRÖF6†–ærÆ—7FVB‡&6R"“°¢Ğ¢&ööÆVâ7W'f—fVBÒ6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓâ6†Ef–Wræ6öçF–ç2†6Æ–VçBÂ¶WB’“°¢–b‚7W'f—fVB’°¢F‡&÷ræWr76W'F–öäW'&÷"‚$6†Df–ÇFW"†–BÆ–æRF†BÖF6†W2æ÷F†–ær–â—G2Æ—7C² ¢²&f–ÇFW"F†B7vÆÆ÷w2WfW'—F†–ær—2v÷'6RF†âæòf–ÇFW""“°¢Ğ¢ÄôttU"æ–æfò‚"6†Df–ÇFW"†–BF†RÆ—7FVBÆ–æRæBÆVgBF†R÷F†W"ÆöæR"“°¢Ğ ¢ò¢ ¢¢ÖVçF–öâ—2æ÷F–f–6F–öâÂ6òF†Ræ÷F–f–6F–öâ6W'f–6R—2v†W&RF†RVffV7B6†÷w2Wà¢ ¢¢Çå6VçB2Æ–W"6†B&F†W"F†âg&öÒF†R6W'fW"6öç6öÆRÂ&V6W6RF†R6Æ–VçBFVÆ–&W&FVÇ¢¢öffW'2F†—2ÖöGVÆRÆ–W"6†BöæÇ’Ò7—7FVÒÆ–æR—2ÇVv–âFÆ¶–ærÂæ÷B6öÖVöæP¢¢FG&W76–ær–÷RâF†Rf—'7BG&gBW6VB6W'fW"'&öF67BæBF†RÖöGVÆRv2&–v‡BFò–væ÷&R—Bà¢ ¢¢ÇåF†RG&–vvW"—2¶W—v÷&Bg&öÒæ÷F†W"7V¶W"â&÷F‚†ÇfW2&Rf÷&6VC¢F†RÖöGVÆR–væ÷&W0¢¢–÷W"÷vâÖW76vW2Â6òF†RÆ–æR6ææ÷B6öÖRg&öÒF†RÆö6ÂÆ–W"ÂæBv—F‚öæÇ’öæR&VÀ¢¢Æ–W"–âF†Rv÷&ÆBF†R6W'fW"†2Fò7V²f÷"6V6öæBöæRà¢¢ğ¢&—fFRfö–B6†DÖVçF–öç2„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂFW7E6–ævÆWÆ–W$6öçFW‡B6–ævÆWÆ–W"’°¢7G&–ær¶W—v÷&BÒ'¦&f&ÆB#°¢6öæf–wW&R†6öçFW‡BÂ$6†DÖVçF–öç2"ÂÖöGVÆRÓâ°¢ÖöGVÆRç6WGF–æw2ç6WE6WGF–ær‚&¶W—v÷&G2"Â¶W—v÷&B“°¢ÖöGVÆRç6WGF–æw2ç6WE6WGF–ær‚&÷väæÖR"ÂfÇ6R“°¢òòF†R7VRv÷VÆB&V6‚f÷"âVF–òFWf–6RF†—2Ö6†–æRFöW2æ÷B†fRà¢ÖöGVÆRç6WGF–æw2ç6WE6WGF–ær‚'6÷VæB"ÂfÇ6R“°¢ÖöGVÆRç6WGF–æw2ç6WE6WGF–ær‚&6ööÆF÷vâ"Âã“°¢Ò“° ¢òòÖVçF–öâ7W&f6W22Fö7BÂæBF†Ræ÷F–f–6F–öç2ÖöGVÆR—2v†BÖ¶W2Fö7G2W†—7C ¢òò—G2öäF—6&ÆR7v—F6†W2F†Rv†öÆRæ÷F–f–6F–öâ6W'f–6RöfbÂæBF†RÆ–fV7–6ÆRFW7BFövvÆV@¢òò—BöfbV&Æ–W"–âF†—2'Vââv—F†÷WBF†—2F†R6W'f–6R6–ÆVçFÇ’G&÷2WfW'’V&Æ—6‚à¢FövvÆR†6öçFW‡BÂ$æ÷F–f–6F–öç2"ÂG'VR“° ¢6öÖV&öG•6—2†6öçFW‡BÂ6–ævÆWÆ–W"Â%6öÖVöæR"Â&6öçG&öÂÆ–æRÖVçF–öæ–ær"²¶W—v÷&B“°¢–b†ÖVçF–öäæ÷F–f–VB†6öçFW‡B’’°¢F‡&÷ræWr76W'F–öäW'&÷"‚&ÖVçF–öâv2&W÷'FVB&Vf÷&R6†DÖVçF–öç2v2Væ&ÆVB"“°¢Ğ ¢FövvÆR†6öçFW‡BÂ$6†DÖVçF–öç2"ÂG'VR“°¢6öÖV&öG•6—2†6öçFW‡BÂ6–ævÆWÆ–W"Â%6öÖVöæR"Â'6V6öæBÆ–æRÖVçF–öæ–ær"²¶W—v÷&B“°¢&ööÆVâæ÷F–6VBÒÖVçF–öäæ÷F–f–VB†6öçFW‡B“°¢FövvÆR†6öçFW‡BÂ$6†DÖVçF–öç2"ÂfÇ6R“°¢FövvÆR†6öçFW‡BÂ$æ÷F–f–6F–öç2"ÂfÇ6R“°¢–b‚æ÷F–6VB’°¢F‡&÷ræWr76W'F–öäW'&÷"‚$6†DÖVçF–öç2F–Bæ÷B&W÷'B6†BÆ–æR6öçF–æ–ær—G2 ¢²&6öæf–wW&VB¶W—v÷&B"“°¢Ğ¢ÄôttU"æ–æfò‚"6†DÖVçF–öç2&W÷'FVB¶W—v÷&B–âÆ–W"6†B"“°¢Ğ ¢ò¢ ¢¢6†BGG&–'WFVBFò6öÖV&öG’v†ò—2æ÷BF†RÆö6ÂÆ–W"à¢ ¢¢Çå6VæF–ær—Bg&öÒF†RÆ–W"–ç7FVBFöW2æ÷Bv÷&²ÂæBF†RÖöGVÆR—2&–v‡B&÷WBF†C¢¢¢Æ–W"6†BÆ–æR'&—fW2&VæFW&VB2´6öFRÅÆ–W#âââçÒÂv†–6‚6öçF–ç2F†RÆ–W"w2÷và¢¢æÖRÂæB6†DÖVçF–öç2FVÆ–&W&FVÇ’&VgW6W2FòG&VB–÷W"÷vâÖW76vR2ÖVçF–öâöb–÷Rà¢¢F†W&R—2öæÇ’öæR&VÂÆ–W"–âFW7Bv÷&ÆBÂ6òF†R6W'fW"7V·2f÷"6V6öæBöæRà¢¢ğ¢&—fFR7FF–2fö–B6öÖV&öG•6—2„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂFW7E6–ævÆWÆ–W$6öçFW‡B6–ævÆWÆ–W"À¢7G&–ær7V¶W"Â7G&–ærFW‡B’°¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óâ6W'fW"ævWEÆ–W$Æ—7B‚’æ'&öF67D6†DÖW76vR€¢òòF†R&VÂÆ–W"w2–BÂv—F‚6öÖV&öG’VÇ6Rw2æÖR&÷VæBFòF†RÆ–æRâÖFR×W–B—0¢òò&V¦V7FVB'’F†R6Æ–VçB26†BfÆ–FF–öâW'&÷"Ò—BöæÇ’66WG2ÖW76vW2g&öĞ¢òò6VæFW'2—B¶æ÷w2&÷WBÒæBF†RF—7Æ’æÖR—2ÆÂF†RÖöGVÆR&VG2ç—v’à¢Æ–W$6†DÖW76vRçVç6–væVB‡6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚’ævWEUT”B‚’ÂFW‡B’À¢6W'fW"æ7&VFT6öÖÖæE6÷W&6U7F6²‚’À¢6†EG—Ræ&–æB„6†EG—Rä4„BÂ6W'fW"ç&Vv—7G'”66W72‚’Â6ö×öæVçBæÆ—FW&Â‡7V¶W"’’’“°¢6öçFW‡Bçv—EF–6·2ƒ“°¢Ğ ¢&—fFR7FF–2&ööÆVâÖVçF–öäæ÷F–f–VB„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡B’°¢&WGW&â6öçFW‡Bæ6ö×WFTöä6Æ–VçB†æ÷F–6R‚&ÖVçF–öæVB–â6†B"“£§FW7B“°¢Ğ ¢ò¢¢Fö7B—26†÷v–ærv†÷6RFW‡B6öçF–ç2F†—2g&vÖVçBâZŠW«®Šğ®+b-jwZ­Ú.¶›­º$zzb¥æÚ±î¸Â¸­yêë¢°k¢G§¦*^*/
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
        // The theme animates its own colours continuously, which is a moving picture behind every
        // frame comparison. This used to be switched off by the HUD scale scenario and left that
        // way, so every render scenario below silently depended on a scenario above it having run -
        // delete or reorder that one and these get noisier for no visible reason. Stillness is owned
        // here now, by the helper whose whole job is holding the scene still.
        stillTheme(context, quiet);
        // F1 toggles; pressing it again on the way out puts the HUD back.
        context.getInput().pressKey(options -> options.keyToggleGui);
        context.waitTicks(20);
    }

    /**
     * Stops, or restarts, everything the theme animates.
     *
     * <p>Through the theme's own reduced-motion switch rather than anything test-only, so what is
     * being held still is a state a player can also be in.
     */
    private static void stillTheme(ClientGameTestContext context, boolean still) {
        context.runOnClient(client -> {
            var service = me.mrhakan.agalarhack.services.ClientServices.require(
                    me.mrhakan.agalarhack.services.ThemeService.class);
            var theme = service.copy();
            theme.reducedMotion = still;
            theme.uiAnimations = !still;
            theme.animationSpeed = 1.0;
            service.preview(theme);
        });
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
            clearEntities(level);
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
            clearEntities(player.level());
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
        // taken at a fixed moment rather thaµ¨¥zºè¯
â¶)à²Ö§uªİ¢ëiºĞk¢G§¦*^m«ëŒ+Š×®º+º$zzb¥æâv†VâF†R7vVW†Vç2Fòf–æ—6‚à¢6öæf–wW&R†6öçFW‡BÂ%7väU5"ÂÖöGVÆRÓâ°¢ÖöGVÆRç6WGF–æw2ç6WE6WGF–ær‚&†÷&—¦öçFÅ&ævR"Â"ã“°¢ÖöGVÆRç6WGF–æw2ç6WE6WGF–ær‚'fW'F–6Å&ævR"ÂBã“°¢Ò“° ¢Ö÷fUF†W&R†6öçFW‡BÂ6–ævÆWÆ–W"Â66VæT&6Ræöfg6WBƒÂÂƒ’“°¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óâ°¢6W'fW%Æ–W"Æ–W"Ò6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚“°¢6W'fW$ÆWfVÂÆWfVÂÒÆ–W"æÆWfVÂ‚“°¢6ÆV$VçF—F–W2†ÆWfVÂ“°¢&Æö6µ÷26VçG&RÒÆ–W"æ&Æö6µ÷6—F–öâ‚“°¢òò6VÆVB6†VÆÃ¢vÆÇ2ÂæB&ööbF‡&VR&Æö6·2W6òF†W&R—27FæF–ær&ööÒVæFW"—Bà¢òòF†RfÆö÷"—2&WÆ6VBFöòÂ&V6W6R7WW&fÆBw2w&72F–W2öæ6R—B—2&ööfVB÷fW"æ@¢òòV6‚FVF‚—2&Æö6²WFFRF†B6VæG2F†R66â&6²FòF†R7F'Bà¢f÷"†–çBG‚ÒÓC²G‚ÃÒC²G‚²²’°¢f÷"†–çBG¢ÒÓC²G¢ÃÒC²G¢²²’°¢ÆWfVÂç6WD&Æö6´æEWFFR†6VçG&Ræöfg6WB†G‚ÂÓÂG¢’Â&Æö6·2å5DôäRæFVfVÇD&Æö6µ7FFR‚’“°¢ÆWfVÂç6WD&Æö6´æEWFFR†6VçG&Ræöfg6WB†G‚Â2ÂG¢’Â&Æö6·2äô%4”D”âæFVfVÇD&Æö6µ7FFR‚’“°¢f÷"†–çBG’Ò²G’ÃÒ#²G’²²’°¢&ööÆVâvÆÂÒÖF‚æ'2†G‚’ÓÒBÇÂÖF‚æ'2†G¢’ÓÒC°¢ÆWfVÂç6WD&Æö6´æEWFFR†6VçG&Ræöfg6WB†G‚ÂG’ÂG¢’ÂvÆÀ¢ò&Æö6·2äô%4”D”âæFVfVÇD&Æö6µ7FFR‚’¢&Æö6·2ä•"æFVfVÇD&Æö6µ7FFR‚’“°¢Ğ¢Ğ¢Ğ¢Ò“°¢6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’çv—Df÷$6‡Væ·5&VæFW"‚“°¢òòÆöærVæ÷Vv‚f÷"F†RÆ–v‡BVæv–æRFòF&¶VâF†R6VÆVBföÇVÖR&Vf÷&Rç—F†–ær—266ææVBà¢6öçFW‡Bçv—EF–6·2ƒƒ“°¢òòBF†RfÆö÷"Âv†W&RF†RÖ&¶W'2vòà¢6öçFW‡BævWD–çWB‚’æÆöö´BƒãbÂcãb“°¢6öçFW‡Bçv—EF–6·2ƒ#“°¢G&w56öÖWF†–ær†6öçFW‡BÂ%7väU5"Â&Ö&¶W'2öâF&²fÆö÷""Âã#RÂãsRÀ¢6Æ–VçBÓâvÆ$†6´6Æ–VçBæÖöGVÆTÖævW"ævWDÖöGVÆR‚%7väU5"¢–ç7Fæ6VöbÖRæ×&†¶âævÆ&†6²æÖöGVÆRç&VæFW"å7väU57và¢bb7vâç&W7VÇG2‚’æ—4V×G’‚’“°¢Ğ ¢ò¢ ¢¢ÖöGVÆRv†÷6Rv†öÆR¦ö"—2Fò&V6öæf–wW&R6†&VB6W'f–6RÂ6òF†R6W'f–6R—2F†RWf–FVæ6Rà¢ ¢¢ÇåF†R&W7F÷&R†Æb—2F†RöæRv÷'F‚76W'F–ærâ6Æ–VçBÆVgBöâF†RÆ÷r&öf–ÆRgFW"F†P¢¢ÖöGVÆRv27v—F6†VBöfbv÷VÆB66â6Æ÷vÇ’f÷"F†R&W7BöbF†R6W76–öâÂæBæ÷F†–æröâ67&VVà¢¢v÷VÆB6’v‡’à¢¢ğ¢&—fFRfö–BW&f÷&Öæ6R„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡B’°¢f"'V–ÇD–âÒÖRæ×&†¶âævÆ&†6²ç6W'f–6W2ç66ææ–ærå66ä'VFvWG2ä$Ää4TC°¢f"vçFVBÒÖRæ×&†¶âævÆ&†6²ç6W'f–6W2ç66ææ–ærå66ä'VFvWG2æf÷%&öf–ÆR‚&Æ÷r"“°¢f"&Vf÷&RÒ66ä'VFvWG2†6öçFW‡B“°¢–b‚'V–ÇD–âæWVÇ2†&Vf÷&R’’°¢F‡&÷ræWr76W'F–öäW'&÷"‚'F†R6†&VB66ææW"v2Ç&VG’öfb—G2'V–ÇBÖ–â6V–Æ–ær‚"²&Vf÷&P¢²"’&Vf÷&RW&f÷&Öæ6R&ã²F†R66Væ&–ò&÷fW2æ÷F†–ær–âF†B7FFR"“°¢Ğ ¢6öæf–wW&R†6öçFW‡BÂ%W&f÷&Öæ6R"ÂÖöGVÆRÓâÖöGVÆRç6WGF–æw2ç6WE6WGF–ær‚'66ä'VFvWB"Â&Æ÷r"’“°¢FövvÆR†6öçFW‡BÂ%W&f÷&Öæ6R"ÂG'VR“°¢6öçFW‡Bçv—EF–6·2ƒ‚“°¢f"Æ–VBÒ66ä'VFvWG2†6öçFW‡B“°¢FövvÆR†6öçFW‡BÂ%W&f÷&Öæ6R"ÂfÇ6R“°¢6öçFW‡Bçv—EF–6·2ƒ‚“°¢f"&W7F÷&VBÒ66ä'VFvWG2†6öçFW‡B“° ¢–b‚vçFVBæWVÇ2†Æ–VB’’°¢F‡&÷ræWr76W'F–öäW'&÷"‚%W&f÷&Öæ6RöâF†RÆ÷r&öf–ÆRÆVgBF†R6†&VB66ææW"B ¢²Æ–VB²"–ç7FVBöb"²vçFVB“°¢Ğ¢–b‚'V–ÇD–âæWVÇ2‡&W7F÷&VB’’°¢F‡&÷ræWr76W'F–öäW'&÷"‚%W&f÷&Öæ6RÆVgBF†R6†&VB66ææW"B"²&W7F÷&V@¢²"gFW"&V–ærF—6&ÆVB–ç7FVBöb&W7F÷&–ær"²'V–ÇD–â“°¢Ğ¢ÄôttU"æ–æfò‚"W&f÷&Öæ6RÆ÷vW&VBF†R6†&VB66ææ–ær6V–Æ–æræBvfR—B&6²"“°¢Ğ ¢&—fFR7FF–2ÖRæ×&†¶âævÆ&†6²ç6W'f–6W2ç66ææ–ærå66ä'VFvWG266ä'VFvWG2„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡B’°¢&WGW&â6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓâÖRæ×&†¶âævÆ&†6²ç6W'f–6W2ä6Æ–VçE6W'f–6W2ç&WV—&R€¢ÖRæ×&†¶âævÆ&†6²ç6W'f–6W2å66ææW%6W'f–6Ræ6Æ72’æ'VFvWG2‚’“°¢Ğ ¢ò¢ ¢¢F†RF–6²f–wW&R—2âW7F–ÖFR'V–ÇBg&öÒ†÷rf"'BF†R6W'fW"w2v÷&ÆB×F–ÖR6¶WG0¢¢'&—fRÂ6òF†R66Væ&–òv—G2f÷"&VÂ6¶WG2&F†W"F†âfVVF–ærF†RÖöGVÆRç—F†–ærà¢ ¢¢Çå–ær—2FVÆ–&W&FVÇ’æ÷B76W'FVC¢F†R–çFVw&FVB6W'fW"&W÷'G2ÆFVæ7’öb¦W&òÂv†–6€¢¢F†RÖöGVÆR6÷'&V7FÇ’FV6Æ–æW2Fò&V6÷&B26×ÆRà¢¢ğ¢&—fFRfö–B6W'fW$–æfò„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡B’°¢&VF–6FSÄÖ–æV7&gCâW7F–ÖF–ærÒ6Æ–VçBÓà¢vÆ$†6´6Æ–VçBæÖöGVÆTÖævW"ævWDÖöGVÆR‚%6W'fW$–æfò"¢–ç7Fæ6VöbÖRæ×&†¶âævÆ&†6²æÖöGVÆRæÖ—62å6W'fW$–æfò–æfğ¢bb–æfòçF–6´W7F–ÖFR‚’æ†4W7F–ÖFR‚¢bb–æfòævWDF—7Æ”æÖR‚’ç7F'G5v—F‚‚%6W'fW$–æfò²"“°¢76W'Dæ÷E–WB†6öçFW‡BÂW7F–ÖF–ærÂ%6W'fW$–æfòÇ&VG’†BF–6²W7F–ÖFR&Vf÷&R—Bv2Væ&ÆVB"“° ¢FövvÆR†6öçFW‡BÂ%6W'fW$–æfò"ÂG'VR“°¢òòv÷&ÆBF–ÖR'&—fW2öæ6RWfW'’GvVçG’F–6·2æBGvòöbF†VÒÖ¶RF†Rf—'7B–çFW'fÂà¢&ööÆVâW7F–ÖFVBÒ6WGFÆR†6öçFW‡BÂW7F–ÖF–ærÂ#“°¢F÷V&ÆRG2Ò6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓà¢‚†ÖRæ×&†¶âævÆ&†6²æÖöGVÆRæÖ—62å6W'fW$–æfò’vÆ$†6´6Æ–VçBæÖöGVÆTÖævW ¢ævWDÖöGVÆR‚%6W'fW$–æfò"’’çF–6´W7F–ÖFR‚’æfW&vR‚’“°¢FövvÆR†6öçFW‡BÂ%6W'fW$–æfò"ÂfÇ6R“° ¢–b‚W7F–ÖFVB’°¢F‡&÷ræWr76W'F–öäW'&÷"‚%6W'fW$–æfò&öGV6VBæòF–6²W7F–ÖFRv—F†–â#F–6·2öb'Vææ–ær6W'fW""“°¢Ğ¢òòv–FRöâW'÷6S¢F†—276W'G2F†RW7F–ÖFR—2&FR&F†W"F†âæöç6Vç6RÂæ÷BF†B¢òò†VFÆW724’'VææW"†—G2GvVçG’à¢–b‡G2ÂãÇÂG2âcã’°¢F‡&÷ræWr76W'F–öäW'&÷"‚%6W'fW$–æfòW7F–ÖFVB"²G2²"G2f÷"â–FÆR–çFVw&FVB6W'fW""“°¢Ğ¢ÄôttU"æ–æfò‚"6W'fW$–æfòW7F–ÖFVB·ÒG2g&öÒF†R6W'fW"w2÷vâF–ÖR6¶WG2"À¢7G&–æræf÷&ÖB†¦fçWF–ÂäÆö6ÆRå$ôõBÂ"Rãb"ÂG2’“°¢Ğ ¢ò¢ ¢¢&VÂ6W'fW"×6–FRvÖRÖÖöFRG&ç6—F–öâö'6W'fVBF‡&÷Vv‚F†R6Æ–VçBw2Æ–W"Æ—7Bà¢ ¢¢ÇåF†RÖöGVÆRFVÆ–&W&FVÇ’öÆÇ2F†R6Æ–VçB×f—6–&ÆRÆ—7B–ç7FVBöbFF–æræ÷F†W"6¶W@¢¢Ö—†–ââF†B¶VW2F†RfVGW&RfW'6–öâ×6fRæBÆ–Ö—G2—BFò–æf÷&ÖF–öâF†R6Æ–VçBÇ&VG¢¢†2âF†R&6VÆ–æR—2W7F&Æ—6†VBv†–ÆRF†RÆö6ÂÆ–W"—2–â7W'f—fÂÂF†VâF†R6W'fW ¢¢6†ævW2F†B6ÖRÆ–W"Fò7&VF—fS²Fö7B&÷fW2F†R6†ævRv2ö'6W'fVB&F†W"F†à¢¢ÖW&VÇ’6†÷v–ærF†RÖöGVÆRw2Væ&ÆVBæ÷F–f–6F–öâà¢¢ğ¢&—fFRfö–BvÖVÖöFTÆW'G2„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂFW7E6–ævÆWÆ–W$6öçFW‡B6–ævÆWÆ–W"’°¢FövvÆR†6öçFW‡BÂ$æ÷F–f–6F–öç2"ÂG'VR“°¢FövvÆR†6öçFW‡BÂ$vÖVÖöFTÆW'G2"ÂfÇ6R“°¢6öæf–wW&R†6öçFW‡BÂ$vÖVÖöFTÆW'G2"ÂÖöGVÆRÓâ°¢ÖöGVÆRç6WGF–æw2ç6WE6WGF–ær‚'6VÆb"ÂG'VR“°¢ÖöGVÆRç6WGF–æw2ç6WE6WGF–ær‚&÷F†W'2"ÂfÇ6R“°¢ÖöGVÆRç6WGF–æw2ç6WE6WGF–ær‚&ÖöFW2"Â&7&VF—fR"“°¢ÖöGVÆRç6WGF–æw2ç6WE6WGF–ær‚&æ÷F–g•Væ¶æ÷vâ"ÂfÇ6R“°¢Ò“° ¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óà¢6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚’ç6WDvÖTÖöFR„vÖUG—Rå5U%d•dÂ’“°¢6öçFW‡Bçv—EF–6·2ƒ#“° ¢FövvÆR†6öçFW‡BÂ$vÖVÖöFTÆW'G2"ÂG'VR“°¢6öçFW‡Bçv—EF–6·2ƒ#“°¢–b†6öçFW‡Bæ6ö×WFTöä6Æ–VçB†æ÷F–6R‚&6†ævVBvÖVÖöFR"“£§FW7B’’°¢FövvÆR†6öçFW‡BÂ$vÖVÖöFTÆW'G2"ÂfÇ6R“°¢FövvÆR†6öçFW‡BÂ$æ÷F–f–6F–öç2"ÂfÇ6R“°¢F‡&÷ræWr76W'F–öäW'&÷"‚$vÖVÖöFTÆW'G2&W÷'FVB6†ævR&Vf÷&RF†R6W'fW"6†ævVB ¢²'F†RÆö6ÂÆ–W"w2vÖRÖöFR"“°¢Ğ ¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óà¢6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚’ç6WDvÖTÖöFR„vÖUG—Rä5$TD•dR’“°¢&ööÆVâæ÷F–6VBÒ6WGFÆR†6öçFW‡BÂæ÷F–6R‚&6†ævVBvÖVÖöFR"“£§FW7BÂ4UEDÄUõD”4µ2“°¢FövvÆR†6öçFW‡BÂ$vÖVÖöFTÆW'G2"ÂfÇ6R“°¢FövvÆR†6öçFW‡BÂ$æ÷F–f–6F–öç2"ÂfÇ6R“° ¢–b‚æ÷F–6VB’°¢F‡&÷ræWr76W'F–öäW'&÷"‚$vÖVÖöFTÆW'G2F–Bæ÷B&W÷'BF†R&VÂ7W'f—fÂ×FòÖ7&VF—fR ¢²'G&ç6—F–öâg&öÒF†R6Æ–VçB×f—6–&ÆRÆ–W"Æ—7B"“°¢Ğ¢ÄôttU"æ–æfò‚"vÖVÖöFTÆW'G2&W÷'FVB&VÂ6VÆbvÖRÖÖöFR6†ævR"“°¢Ğ ¢ò¢ ¢¢F†R7&—B'VÆR&VBv–ç7BF†RÆ–W"w2&VÂ7FFS¢æò7&—Bv—F‚&÷F‚fVWBöâF†Rw&÷VæBÂ¢¢7&—BöâF†Rv’F÷vââæ÷F†–ær—2fVBFòF†RÖöGVÆR(	B—B&VG2F†R6ÖRÆ–W"F†RvÖRFöW2À¢¢v†–6‚—2F†RöæÇ’v’FòFVÆÂ6÷'&V7B'VÆRg&öÒöæRF†BÇv—2ç7vW'2F†R6ÖRà¢¢ğ¢&—fFRfö–B7&—D–æfò„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂFW7E6–ævÆWÆ–W$6öçFW‡B6–ævÆWÆ–W"’°¢&VF–6FSÄÖ–æV7&gCâ7&—GF–ærÒ6Æ–VçBÓà¢vÆ$†6´6Æ–VçBæÖöGVÆTÖævW"ævWDÖöGVÆR‚$7&—D–æfò"¢–ç7Fæ6VöbÖRæ×&†¶âævÆ&†6²æÖöGVÆRæ6öÖ&Bä7&—D–æfò7&—@¢bb7&—Bæ7&—E&VG’‚¢bb$7&—D–æfò·&VG•Ò"æWVÇ2†7&—BævWDF—7Æ”æÖR‚’“° ¢Ö÷fUF†W&R†6öçFW‡BÂ6–ævÆWÆ–W"Â66VæT&6Ræöfg6WBƒÂÂ#’“°¢FövvÆR†6öçFW‡BÂ$7&—D–æfò"ÂG'VR“°¢6öçFW‡Bçv—EF–6·2ƒ#“°¢–b†6öçFW‡Bæ6ö×WFTöä6Æ–VçB†7&—GF–æs£§FW7B’’°¢FövvÆR†6öçFW‡BÂ$7&—D–æfò"ÂfÇ6R“°¢F‡&÷ræWr76W'F–öäW'&÷"‚$7&—D–æfò6–B†—Bv÷VÆB7&—Bv†–ÆRF†RÆ–W"7FööB7F–ÆÂöâF†Rw&÷VæB"“°¢Ğ ¢òò7&VF—fRÂ6òF†—'G’&Æö6·2—2fÆÂ&F†W"F†âFVF‚à¢&Æö6µ÷2&÷fRÒ66VæT&6Ræöfg6WBƒÂ3Â#“°¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óâ°¢6W'fW%Æ–W"Æ–W"Ò6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚“°¢Æ–W"çFVÆW÷'EFò†&÷fRævWE‚‚’²ãRÂ&÷fRævWE’‚’Â&÷fRævWE¢‚’²ãR“°¢Æ–W"ç6WDFVÇFÖ÷fVÖVçB…fV32å¤U$ò“°¢Ò“°¢&ööÆVâ7&—G2Ò6WGFÆR†6öçFW‡BÂ7&—GF–ærÂc“°¢FövvÆR†6öçFW‡BÂ$7&—D–æfò"ÂfÇ6R“°¢–b‚7&—G2’°¢F‡&÷ræWr76W'F–öäW'&÷"‚$7&—D–æfòæWfW"&W÷'FVB7&—F–6Âv†–ÆRF†RÆ–W"v2fÆÆ–ær ¢²'F†—'G’&Æö6·2Âv†–6‚—2W†7FÇ’F†R7FFR#bã"7&—G2–â"“°¢Ğ¢6öçFW‡Bçv—EF–6·2ƒC“°¢ÄôttU"æ–æfò‚"7&—D–æfòFöÆBw&÷VæFVBÆ–W"g&öÒfÆÆ–æröæR"“°¢Ğ ¢ò¢ ¢¢æV&Ç’v÷&âÖ÷WBVÇ—G&6†÷VÆB&RæWw2&Vf÷&RF†RfÆ–v‡BÂæ÷BGW&–ær—Bà¢ ¢¢ÇåF†RGW&&–Æ—G’f–wW&R—2&VBöfbF†Rv÷&â—FVÒ&F†W"F†âwVW76VBÂ6òF†R76W'F–öâ—2öà¢¢F†RW†7BçVÖ&W#¢ÖöGVÆRF†Bv&æVBv—F‚F†R&–v‡BFW‡BæBF†Rw&öær6÷VçBv÷VÆB&Ræğ¢¢W6RFò6öÖV&öG’FV6–F–ærv†WF†W"FòÆVæ6‚à¢¢ğ¢&—fFRfö–BVÇ—G&–æfò„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂFW7E6–ævÆWÆ–W$6öçFW‡B6–ævÆWÆ–W"’°¢òòFö7G2öæÇ’W†—7Bv†–ÆRF†Ræ÷F–f–6F–öç2ÖöGVÆR—2öã²F†RÆ–fV7–6ÆRFW7BÆVgB—Böfbà¢FövvÆR†6öçFW‡BÂ$æ÷F–f–6F–öç2"ÂG'VR“° ¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óâ°¢6W'fW%Æ–W"Æ–W"Ò6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚“°¢—FVÕ7F6²VÇ—G&ÒæWr—FVÕ7F6²„—FV×2äTÅ•E$“°¢òòFVâW6W2ÆVgBÂvVÆÂVæFW"F†RGvVçG’×W6RFVfVÇBF‡&W6†öÆBà¢VÇ—G&ç6WDFÖvUfÇVR†VÇ—G&ævWDÖ„FÖvR‚’Ò“°¢Æ–W"ç6WD—FVÕ6Æ÷B„WV—ÖVçE6Æ÷Bä4„U5BÂVÇ—G&“°¢Ò“°¢6öçFW‡Bçv—EF–6·2ƒ#“°¢76W'Dæ÷E–WB†6öçFW‡BÂæ÷F–6R‚$VÇ—G&B"’Â&v÷&âVÇ—G&v2&W÷'FVB&Vf÷&RVÇ—G&–æfòv2Væ&ÆVB"“° ¢FövvÆR†6öçFW‡BÂ$VÇ—G&–æfò"ÂG'VR“°¢&ööÆVâv&æVBÒ6WGFÆR†6öçFW‡BÂæ÷F–6R‚$VÇ—G&BW6W2"’“°¢7G&–ærÆ&VÂÒ6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓà¢vÆ$†6´6Æ–VçBæÖöGVÆTÖævW"ævWDÖöGVÆR‚$VÇ—G&–æfò"’ævWDF—7Æ”æÖR‚’“°¢FövvÆR†6öçFW‡BÂ$VÇ—G&–æfò"ÂfÇ6R“°¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óâ6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚¢ç6WD—FVÕ6Æ÷B„WV—ÖVçE6Æ÷Bä4„U5BÂ—FVÕ7F6²äTÕE’’“° ¢–b‚v&æVB’°¢F‡&÷ræWr76W'F–öäW'&÷"‚$VÇ—G&–æfòF–Bæ÷Bv&â&÷WBâVÇ—G&v—F‚FVâW6W2ÆVgBv—F†–â ¢²4UEDÄUõD”4µ2²"F–6·2"“°¢Ğ¢–b‚Æ&VÂç7F'G5v—F‚‚$VÇ—G&–æfò³GW""’’°¢F‡&÷ræWr76W'F–öäW'&÷"‚$VÇ—G&–æfòv&æVB'WB—G2ÖöGVÆRÖÆ—7BÆ&VÂ&VB"²Æ&VÀ¢²"&F†W"F†âF†RFVâW6W2ÆVgBöâF†Rv÷&âVÇ—G&"“°¢Ğ¢ÄôttU"æ–æfò‚"VÇ—G&–æfòv&æVB&÷WBv÷&âVÇ—G&æB6÷VçFVB—G2W6W2"“°¢Ğ ¢ò¢ ¢¢F÷FVÒ—2÷VBf÷"&VÃ¢F†RÆ–W"—2WB–â7W'f—fÂÂ†æFVBöæRÂæBFVÇBÖ÷&RFÖvP¢¢F†âF†W’†fR†VÇF‚à¢ ¢¢Çä÷&F–æ'’Öv–2FÖvR&F†W"F†â´6öFR¶–ÆÂ‚—ÒÂv†–6‚—2FvvVB2'—76–æp¢¢–çgVÆæW&&–Æ—G’æBv÷VÆBF¶RF†RÆ–W"7G&–v‡B7BF†RF÷FVÒF†R66Væ&–ò—2&÷WBà¢¢ğ¢&—fFRfö–BF÷FVÕG&6¶W"„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂFW7E6–ævÆWÆ–W$6öçFW‡B6–ævÆWÆ–W"’°¢FövvÆR†6öçFW‡BÂ$æ÷F–f–6F–öç2"ÂG'VR“°¢Ö÷fUF†W&R†6öçFW‡BÂ6–ævÆWÆ–W"Â66VæT&6Ræöfg6WBƒÂÂ3c’“°¢7G&–ærv†òÒ6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓâ6Æ–VçBçÆ–W"ævWDæÖR‚’ævWE7G&–ær‚’“° ¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óâ°¢6W'fW%Æ–W"Æ–W"Ò6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚“°¢Æ–W"ç6WDvÖTÖöFR„vÖUG—Rå5U%d•dÂ“°¢Æ–W"ç6WD†VÇF‚‡Æ–W"ævWDÖ„†VÇF‚‚’“°¢Æ–W"ç6WD—FVÕ6Æ÷B„WV—ÖVçE6Æ÷Bäôdd„äBÂæWr—FVÕ7F6²„—FV×2åDõDTÕôôeõTäE””är’“°¢Ò“°¢6öçFW‡Bçv—EF–6·2ƒ#“° ¢FövvÆR†6öçFW‡BÂ%F÷FVÕG&6¶W""ÂG'VR“°¢&VF–6FSÄÖ–æV7&gCâ6÷VçFVBÒ6Æ–VçBÓà¢vÆ$†6´6Æ–VçBæÖöGVÆTÖævW"ævWDÖöGVÆR‚%F÷FVÕG&6¶W""¢–ç7Fæ6VöbÖRæ×&†¶âævÆ&†6²æÖöGVÆRæ6öÖ&BåF÷FVÕG&6¶W"G&6¶W ¢bbG&6¶W"ç÷4f÷"‡v†ò’ÓÒ¢bb%F÷FVÕG&6¶W"³Ò"æWVÇ2‡G&6¶W"ævWDF—7Æ”æÖR‚’“°¢–b†6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6÷VçFVC£§FW7B’’°¢FövvÆR†6öçFW‡BÂ%F÷FVÕG&6¶W""ÂfÇ6R“°¢F‡&÷ræWr76W'F–öäW'&÷"‚%F÷FVÕG&6¶W"6÷VçFVB÷&Vf÷&Rç’F÷FVÒ†B&VVâW6VB"“°¢Ğ ¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óâ°¢6W'fW%Æ–W"Æ–W"Ò6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚“°¢Æ–W"æ‡W'E6W'fW"‡Æ–W"æÆWfVÂ‚’ÂÆ–W"æÆWfVÂ‚’æFÖvU6÷W&6W2‚’æÖv–2‚’Âãb“°¢Ò“°¢&ööÆVâ6u÷Ò6WGFÆR†6öçFW‡BÂ6÷VçFVB“°¢&ööÆVâææ÷Væ6VBÒ6öçFW‡Bæ6ö×WFTöä6Æ–VçB†æ÷F–6R‚'÷VBF÷FVÒ‡6VVâ’"“£§FW7B“°¢FövvÆR†6öçFW‡BÂ%F÷FVÕG&6¶W""ÂfÇ6R“° ¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óâ°¢6W'fW%Æ–W"Æ–W"Ò6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚“°¢Æ–W"ç6WD—FVÕ6Æ÷B„WV—ÖVçE6Æ÷Bäôdd„äBÂ—FVÕ7F6²äTÕE’“°¢Æ–W"ç6WD†VÇF‚‡Æ–W"ævWDÖ„†VÇF‚‚’“°¢Æ–W"ç6WDvÖTÖöFR„vÖUG—Rä5$TD•dR“°¢òòF÷FVÒÆVfW2&VvVæW&F–öâæB'6÷'F–öâ'Vææ–ærf÷"F†RæW‡Bf÷'G’Öf—fR6V6öæG2À¢òòæBF†V—"'F–6ÆW2G&–gBF‡&÷Vv‚F†R6ÖW&âF†R&VæFW"66Væ&–÷2F†BföÆÆ÷p¢òòÖV7W&R7F–ÆÂg&ÖRv–ç7B7F–ÆÂg&ÖRÂæBF†B—2æ÷B7F–ÆÂà¢Æ–W"ç&VÖ÷fTÆÄVffV7G2‚“°¢Ò“°¢6öçFW‡Bçv—EF–6·2ƒ“° ¢–b‚6u÷’°¢F‡&÷ræWr76W'F–öäW'&÷"‚%F÷FVÕG&6¶W"F–Bæ÷B6÷VçBF†RF÷FVÒF†RÆ–W"§W7B÷VBv—F†–â ¢²4UEDÄUõD”4µ2²"F–6·2"“°¢Ğ¢–b‚ææ÷Væ6VB’°¢F‡&÷ræWr76W'F–öäW'&÷"‚%F÷FVÕG&6¶W"6÷VçFVBF†R÷'WBV&Æ—6†VBæòæ÷F–f–6F–öâ&÷WB—B"“°¢Ğ¢ÄôttU"æ–æfò‚"F÷FVÕG&6¶W"6÷VçFVBF÷FVÒF†RÆ–W"7GVÆÇ’÷VB"“°¢Ğ ¢ò¢ ¢¢F†RÆörf–ÆÇ2g&öÒ6öÖ&BÂæ÷Bg&öÒ&V–ær7v—F6†VBöââF†R6öçG&öÂ†Æb—2F†Rö–çC¢F†P¢¢ÖöGVÆR6—G2Væ&ÆVBv—F‚¦öÖ&–R–âg&öçBöb—BæB&V6÷&G2æ÷F†–ærVçF–ÂW&Âv†–6‚—2v†@¢¢6WG2F†R6†&VBF&vWBÂ—27v—F6†VBöâFöòà¢¢ğ¢ò¢ ¢¢v†WF†W"F†RFÖvRÖVæ6†çFÖVçBfÖ–Æ–W2&R7GVÆÇ’&V6övæ—6VBöâÆ—fR6Æ–VçBà¢ ¢¢Çç´6öFR–çfVçF÷'•6W'f–6RæfÖ–Ç”ögÒFV6–FW2v†WF†W"6Ö—FR÷"&æRöb'F‡&÷öG2vV–v‡F–æp¢¢Æ–W2ÂæB—BFV6–FW2—B'’6¶–ærv†WF†W"F†RVçF—G’G—R—2–âfæ–ÆÆFrâVçF—G’×G—P¢¢Fw2&RFFÂ6VçB'’F†R6W'fW"æB&÷VæBöçFòF†R&Vv—7G'’†öÆFW'2v†VâF†W’'&—fRÂ6òF†P¢¢v†öÆR'VÆR6âV–WFÇ’ç7vW"tTäU$”2f÷"WfW'—F†–ær–bF†B&–æF–ær—2æ÷BF†W&RöâF†P¢¢6Æ–VçBÒv—F‚æòW†6WF–öâæBæòÆörÆ–æRâæ÷F†–ærFW7FVB—C¢WFõvVöâw2÷vâ66Væ&–ò–×0¢¢Bâ&Ö÷W"7FæBÂv†–6‚—2tTäU$”2Â6ò&÷F‚FvvVB'&æ6†W2vW&RVæW†W&6—6VBà¢ ¢¢ÇåF†—27vç2F†RGvòÖö'2F†RFw2W†—7Bf÷"æB76W'G2F†R6Æ76–f–6F–öâF—&V7FÇ’Âv†–6€¢¢Ç6òÖ¶W2F†RÖ–w&F–öâöbF†RFW&V6FVB&Vv—7G'’66W76÷"&V†–æB—B6†ævRv—F‚FW7@¢¢VæFW"—B&F†W"F†â†÷VgVÂVF—Bà¢¢ğ¢&—fFRfö–BvVöäfÖ–Æ–W2„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂFW7E6–ævÆWÆ–W$6öçFW‡B6–ævÆWÆ–W"’°¢Ö÷fUF†W&R†6öçFW‡BÂ6–ævÆWÆ–W"Â66VæT&6Ræöfg6WBƒÂÂ#’“°¢–çEµÒ–G2Ò6–ævÆWÆ–W"ævWE6W'fW"‚’æ6ö×WFTöå6W'fW"‡6W'fW"Óâ°¢6W'fW%Æ–W"Æ–W"Ò6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚“°¢6ÆV$VçF—F–W2‡Æ–W"æÆWfVÂ‚’“°¢&Æö6µ÷2†W&RÒÆ–W"æ&Æö6µ÷6—F–öâ‚“°¢f"¦öÖ&–RÒVçF—G•G—W2å¤ôÔ$”Rç7vâ‡Æ–W"æÆWfVÂ‚’Â†W&Ræöfg6WBƒ"ÂÂ’À¢VçF—G•7vå&V6öâä4ôÔÔäB“°¢f"7–FW"ÒVçF—G•G—W2å5”DU"ç7vâ‡Æ–W"æÆWfVÂ‚’Â†W&Ræöfg6WB‚Ó"ÂÂ’À¢VçF—G•7vå&V6öâä4ôÔÔäB“°¢f"–rÒVçF—G•G—W2å”rç7vâ‡Æ–W"æÆWfVÂ‚’Â†W&Ræöfg6WBƒÂÂ"’À¢VçF—G•7vå&V6öâä4ôÔÔäB“°¢–b‡¦öÖ&–RÓÒçVÆÂÇÂ7–FW"ÓÒçVÆÂÇÂ–rÓÒçVÆÂ’°¢F‡&÷ræWr76W'F–öäW'&÷"‚&6÷VÆBæ÷B7vâF†RF‡&VRÖö'2F†—266Væ&–ò6Æ76–f–W2"“°«ZŠW«®Šğ®+b-jwZ­Ú.¶›­º$zzb¥æÚ±î¸Â¸­yêë¢°k¢G§¦*^            }
            zombie.setNoAi(true);
            spider.setNoAi(true);
            pig.setNoAi(true);
            return new int[]{zombie.getId(), spider.getId(), pig.getId()};
        });
        context.waitTicks(20);

        String families = context.computeOnClient(client -> {
            StringBuilder seen = new StringBuilder();
            for (int id : ids) {
                var entity = client.level.getEntity(id);
                if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) {
                    return "the client has no living entity for id " + id;
                }
                seen.append(entity.getType().getDescriptionId()).append('=')
                        .append(me.mrhakan.agalarhack.services.InventoryService.familyOf(living))
                        .append(' ');
            }
            return seen.toString().strip();
        });

        var expected = context.computeOnClient(client -> {
            var zombie = (net.minecraft.world.entity.LivingEntity) client.level.getEntity(ids[0]);
            var spider = (net.minecraft.world.entity.LivingEntity) client.level.getEntity(ids[1]);
            var pig = (net.minecraft.world.entity.LivingEntity) client.level.getEntity(ids[2]);
            return me.mrhakan.agalarhack.services.InventoryService.familyOf(zombie)
                    == me.mrhakan.agalarhack.services.ItemScoring.TargetFamily.UNDEAD
                    && me.mrhakan.agalarhack.services.InventoryService.familyOf(spider)
                    == me.mrhakan.agalarhack.services.ItemScoring.TargetFamily.ARTHROPOD
                    && me.mrhakan.agalarhack.services.InventoryService.familyOf(pig)
                    == me.mrhakan.agalarhack.services.ItemScoring.TargetFamily.GENERIC;
        });
        if (!expected) {
            throw new AssertionError("the damage families came back as [" + families + "]. A zombie "
                    + "must be UNDEAD and a spider ARTHROPOD; all-GENERIC means the entity-type tags "
                    + "are not bound on the client, so Smite and Bane of Arthropods weighting never "
                    + "applies and AutoWeapon silently ignores both enchantments");
        }
        singleplayer.getServer().runOnServer(server ->
                clearEntities(singleplayer.getConnection().getServerPlayer().level()));
        LOGGER.info("  weapon families: {}", families);
    }

    private void combatHistory(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        moveThere(context, singleplayer, sceneBase.offset(0, 0, 240));
        BlockPos where = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            clearEntities(player.level());
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
            // Read-only, so nothing is being removed under the iterator here; the null guard is
            // for consistency with clearEntities rather than because this one has ever seen one.
            for (Entity entity : player.level().getAllEntities()) {
                if (entity == null || entity instanceof ServerPlayer) continue;
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
            clearEntities(singleplayer.getConnection().getServerPlayer().level());
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
     * would have found it runs. The claim is unaffected â€” one warning is one warning.
     */
    private void projectileWarning(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        toggle(context, "Notifications", true);
        moveThere(context, singleplayer, sceneBase.offset(0, 0, 300));
        singleplayer.getServer().runOnServer(server -> {
            clearEntities(singleplayer.getConnection().getServerPlayer().level());
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
            clearEntities(singleplayer.getConnection().getServerPlayer().level());
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
            clearEntities(level);
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
            clearEntities(singleplayer.getConnection().getServerPlayer().level());
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

        toggle(context, "Autµ¨¥zºè¯
â¶)à²Ö§uªİ¢ëiºĞk¢G§¦*^m«ëŒ+Š×®º+º$zzb¥æõ&W7vâ"ÂG'VR“°¢&ööÆVâ&W7væVBÒ6WGFÆR†6öçFW‡BÂöäFVF…67&VVâææVvFR‚’Â4UEDÄUõD”4µ2¢"“°¢FövvÆR†6öçFW‡BÂ$WFõ&W7vâ"ÂfÇ6R“°¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óà¢6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚’ç6WDvÖTÖöFR„vÖUG—Rä5$TD•dR’“° ¢–b‚&W7væVB’°¢F‡&÷ræWr76W'F–öäW'&÷"‚$WFõ&W7vâÆVgBF†RÆ–W"öâF†RFVF‚67&VVâf÷" ¢²…4UEDÄUõD”4µ2¢"’²"F–6·2"“°¢Ğ¢ÄôttU"æ–æfò‚"WFõ&W7vâ6ÆV&VBFVF‚67&VVâF†RvÖRÆVgB7FæF–ær"“°¢Ğ ¢ò¢ ¢¢F†R67B—2&VÂæBF†R&—FR—2æ÷Bà¢ ¢¢Çä67F–ær—276W'FVBVæBFòVæC¢&öBvöW2–âF†R†÷F&"ÂF†RÖöGVÆR—27v—F6†VBöâÂæB¢¢f—6†–ær†öö²V'2&V6W6RF†RÖöGVÆR6VÆV7FVBF†R6Æ÷BæB&W76VBW6Râæ÷F†–ær&÷WBF†@¢¢—26–×VÆFVBà¢ ¢¢ÇåF†R&—FR—2â6W'fW"–6·2—G2÷vâÖöÖVçB&WGvVVâf—fRæBF†—'G’6V6öæG2Âv†–6‚—2Æöæp¢¢F–ÖRFò†öÆBFW7B÷Vâf÷"&W7VÇBF†B—26ö–âF÷72öâ6Æ÷r'VææW"âv†BF†RÖöGVÆP¢¢7GVÆÇ’vF6†W2f÷"—2F†R&ö&&W"&V–ærVÆÆVBVæFW"(	B—B6—26òÂæB—B6ææ÷B¶æ÷rf—6€¢¢—2F†W&R(	B6òF†R&ö&&W"—2VÆÆVBVæFW"öâF†R6W'fW"–ç7FVBâF†B—2F†R7VR&W&öGV6VBÀ¢¢æ÷BF†RÖöGVÆRw2FV6—6–öã¢v†WF†W"—B&VVÇ2–âÂ†÷rÆöær—Bv—G2æBv†WF†W"—B67G2v–à¢¢&R7F–ÆÂVçF—&VÇ’F†RÖöGVÆRw2âf—6†–ærv–ç7B&VÂ6W'fW"öâ&VÂ6F6‚7F—2öâF†P¢¢ÖçVÂ66WFæ6RÆ—7Bà¢¢ğ¢&—fFRfö–BWFôf—6‚„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂFW7E6–ævÆWÆ–W$6öçFW‡B6–ævÆWÆ–W"’°¢Ö÷fUF†W&R†6öçFW‡BÂ6–ævÆWÆ–W"Â66VæT&6Ræöfg6WBƒÂÂ3“’“°¢&Æö6µ÷2ööÂÒ6–ævÆWÆ–W"ævWE6W'fW"‚’æ6ö×WFTöå6W'fW"‡6W'fW"Óâ°¢6W'fW%Æ–W"Æ–W"Ò6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚“°¢6W'fW$ÆWfVÂÆWfVÂÒÆ–W"æÆWfVÂ‚“°¢6ÆV$VçF—F–W2†ÆWfVÂ“°¢òòv–FRööÂ7F'F–ærF‡&VR&Æö6·2†VBÂGvòFVW6ò&ö&&W"fÆöG2&F†W"F†à¢òò&W7F–æröâF†R&÷GFöÒâæòvÆÂ—2'V–ÇB&÷VæB—C¢F†RVçF÷V6†VB7WW&fÆBFW'&–â@¢òòF†R6ÖRFWF‚Ç&VG’†öÆG2F†RvFW"–ââF†Rf—'7BfW'6–öâ&—6VB7FöæRÆ—æ@¢òòF†RÆ—7FööBB†VB†V–v‡B&WGvVVâF†RÆ–W"æBF†RvFW"Â6òWfW'’67B†—B—BĞ¢òòv†–6‚F†R66Væ&–ò&W÷'FVB2F†R&ö&&W"æWfW"&V6†–ærF†RvFW"Â6÷'&V7FÇ’à¢&Æö6µ÷26VçG&RÒÆ–W"æ&Æö6µ÷6—F–öâ‚’æöfg6WBƒ‚ÂÂ“°¢f÷"†–çBG‚ÒÓs²G‚ÃÒs²G‚²²’°¢f÷"†–çBG¢ÒÓs²G¢ÃÒs²G¢²²’°¢ÆWfVÂç6WD&Æö6´æEWFFR†6VçG&Ræöfg6WB†G‚ÂÂG¢’Â&Æö6·2ä•"æFVfVÇD&Æö6µ7FFR‚’“°¢f÷"†–çBG’ÒÓ#²G’ÃÒÓ²G’²²’°¢ÆWfVÂç6WD&Æö6´æEWFFR†6VçG&Ræöfg6WB†G‚ÂG’ÂG¢’Â&Æö6·2åtDU"æFVfVÇD&Æö6µ7FFR‚’“°¢Ğ¢Ğ¢Ğ¢&WGW&â6VçG&S°¢Ò“°¢6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’çv—Df÷$6‡Væ·5&VæFW"‚“°¢6WD–çfVçF÷'’‡6–ævÆWÆ–W"Â6Æ÷G2Óâ°¢6Æ÷G2ç6WD—FVÒƒÂæWr—FVÕ7F6²„—FV×2äd•4„”äuõ$ôB’“°¢6Æ÷G2ç6WD—FVÒƒÂæWr—FVÕ7F6²„—FV×2äd•4„”äuõ$ôB’“°¢6Æ÷G2ç6WE6VÆV7FVE6Æ÷Bƒ“°¢Ò“°¢òò6WD–çfVçF÷'’VF—G2F†R6W'fW"Æ–W"w2–çfVçF÷'’æB'&öF67G26öçF–æW"6Æ÷G2Â'WBF†P¢òò6VÆV7FVB†÷F&"–æFW‚—2æ÷B6öçF–æW"6Æ÷BâÖ—'&÷"—BFòF†R6Æ–VçBFöò6òF†—0¢òò66Væ&–ò7F'G2v—F‚F†R6ÖR6VÆV7FVB&öBöâ&÷F‚6–FW2öbF†R6–ævÆWÆ–W"6öææV7F–öâà¢6öçFW‡Bç'Väöä6Æ–VçB†6Æ–VçBÓâ6Æ–VçBçÆ–W"ævWD–çfVçF÷'’‚’ç6WE6VÆV7FVE6Æ÷Bƒ’“°¢òòBF†RÖ–FFÆRöbF†RööÂâ—B—2F†—'FVVâ&Æö6·27&÷72Â6òç’÷&F–æ'’67BÆæG2–â—Bà¢6öçFW‡BævWD–çWB‚’æÆöö´B‡ööÂ“°¢6öçFW‡Bçv—EF–6·2ƒ#“°¢–b†6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓâ6Æ–VçBçÆ–W"ævWD–çfVçF÷'’‚’ævWE6VÆV7FVE6Æ÷B‚’’Ò’°¢F‡&÷ræWr76W'F–öäW'&÷"‚$WFôf—6‚vÖR×FW7B6WGWF–Bæ÷B6VÆV7BF†R–çFVæFVB&öBöâF†R6Æ–VçB"“°¢Ğ ¢&VF–6FSÄÖ–æV7&gCâ†öö¶VBÒ6Æ–VçBÓâ6Æ–VçBçÆ–W"æf—6†–ærÒçVÆÂbb6Æ–VçBçÆ–W"æf—6†–æræ—4Æ—fR‚“°¢76W'Dæ÷E–WB†6öçFW‡BÂ†öö¶VBÂ&f—6†–ær†öö²v2Ç&VG’÷WB&Vf÷&RWFôf—6‚&â"“° ¢FövvÆR†6öçFW‡BÂ$WFôf—6‚"ÂG'VR“°¢&ööÆVâ67BÒ6WGFÆR†6öçFW‡BÂ†öö¶VB“°¢–b‚67B’°¢FövvÆR†6öçFW‡BÂ$WFôf—6‚"ÂfÇ6R“°¢F‡&÷ræWr76W'F–öäW'&÷"‚$WFôf—6‚æWfW"67Bv—F‚&öB–âF†R6VÆV7FVB†÷F&"6Æ÷B"“°¢Ğ¢–çB6VÆV7FVE&öBÒ6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓâ6Æ–VçBçÆ–W"ævWD–çfVçF÷'’‚’ævWE6VÆV7FVE6Æ÷B‚’“°¢–b‡6VÆV7FVE&öBÒ’°¢FövvÆR†6öçFW‡BÂ$WFôf—6‚"ÂfÇ6R“°¢F‡&÷ræWr76W'F–öäW'&÷"‚$WFôf—6‚6†ævVBF†R6VÆV7FVBf—6†–ær&öBg&öÒ6Æ÷BFò6Æ÷B"²6VÆV7FVE&öB“°¢Ğ¢òòv—F–ærf÷"F†R&ö&&W"Fò6WGFÆR–âF†RvFW#¢F†RFWFV7F÷"–væ÷&W2†öö²F†B—2æ÷B–â—Bà¢fV327F'BÒ÷6—F–öâ†6öçFW‡B“°¢&ööÆVâfÆöF–ærÒ6WGFÆR†6öçFW‡BÂ6Æ–VçBÓâ6Æ–VçBçÆ–W"æf—6†–ærÒçVÆÀ¢bb6Æ–VçBçÆ–W"æf—6†–æræ—4–åvFW"‚’“°¢7G&–ær&ö&&W"Ò6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓâ6Æ–VçBçÆ–W"æf—6†–ærÓÒçVÆÂò&vöæR ¢¢&B"²6Æ–VçBçÆ–W"æf—6†–ærç÷6—F–öâ‚’²"–åvFW#Ò ¢²6Æ–VçBçÆ–W"æf—6†–æræ—4–åvFW"‚’²"öäw&÷VæCÒ ¢²6Æ–VçBçÆ–W"æf—6†–æræöäw&÷VæB‚’“°¢–çBf—'7D†öö²Ò6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓâ6Æ–VçBçÆ–W"æf—6†–ærævWD–B‚’“° ¢òò¶VWVÆÆ–ærF†R&ö&&W"VæFW"VçF–ÂF†RÖöGVÆR&V7G2Â&F†W"F†âW6†–ær—Böæ6Rà¢òğ¢òòöæR–×VÇ6Rv26ö–âF÷72ÂæB—BÆ÷7B4’'Vã¢F†R6W'fW"6WG2F†RfVÆö6—G’ÂF†P¢òò6Æ–VçBw2÷vâ†öö²‡—6–72F†VâÇ’vFW"G&ræB'V÷–æ7’öâF÷öb—BÂæBF†RÖöGVÆP¢òòöæÇ’&VVÇ2–b—B†Vç2Fò6×ÆRF–6²v†W&RF†R6Æ–VçBw26÷’—27F–ÆÂFW66VæF–æp¢òòf7FW"F†âF†RFWFV7F÷"w2F‡&W6†öÆBâ&VÂ&—FR—2ÇVævRÆ7F–ær6WfW&ÂF–6·2ÒF†@¢òò—2v‡’F†RFWFV7F÷"7W&W76W2&WVG2BÆÂÒ6òF†—2&W&öGV6W2ÇVævR–ç7FVBöb¢òò6–ævÆRg&ÖRöböæRâ—BFöW2æ÷B&VÆ‚v†B—276W'FVC¢F†R7VR—27F–ÆÂöæÇ’&ö&&W ¢òòÖ÷f–ærF÷vâÂæBv†WF†W"Fò&VVÂ—27F–ÆÂVçF—&VÇ’F†RÖöGVÆRw2FV6—6–öâà¢F÷V&ÆRFVWW7BÒ°¢&ööÆVâ&VVÆVBÒfÇ6S°¢–b†fÆöF–ær’°¢f÷"†–çB&÷VæBÒ²&÷VæBÂbb&VVÆVC²&÷VæB²²’°¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óâ°¢f"†öö²Ò6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚’æf—6†–æs°¢òòvVÆÂ7BF†RFVfVÇBF‡&W6†öÆBöbV–v‡B‡VæG&VGF‡2öb&Æö6²W"F–6²à¢–b††öö²ÒçVÆÂ’†öö²ç6WDFVÇFÖ÷fVÖVçBƒãÂÓãBÂã“°¢Ò“°¢6öçFW‡Bçv—EF–6·2ƒ"“°¢FVWW7BÒÖF‚æÖ–â†FVWW7BÂ6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓà¢6Æ–VçBçÆ–W"æf—6†–ærÓÒçVÆÂòã ¢¢6Æ–VçBçÆ–W"æf—6†–ærævWDFVÇFÖ÷fVÖVçB‚’ç’’“°¢&VVÆVBÒ6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓâ6Æ–VçBçÆ–W"æf—6†–ærÓÒçVÆÀ¢ÇÂ6Æ–VçBçÆ–W"æf—6†–ærævWD–B‚’Òf—'7D†öö²“°¢Ğ¢òòF†R&VVÂ—G6VÆbv—G2&VVÄFVÆ–F–6·2gFW"F†RVÆÂ—2&V6övæ—6VBÂ6òv—fRF†P¢òòÖöGVÆRF†BÆöæröæ6RF†R7VR†27F÷VB&V–ærÆ–VBà¢–b‚&VVÆVB’°¢&VVÆVBÒ6WGFÆR†6öçFW‡BÂ6Æ–VçBÓâ6Æ–VçBçÆ–W"æf—6†–ærÓÒçVÆÀ¢ÇÂ6Æ–VçBçÆ–W"æf—6†–ærævWD–B‚’Òf—'7D†öö²“°¢Ğ¢Ğ¢FövvÆR†6öçFW‡BÂ$WFôf—6‚"ÂfÇ6R“°¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óâ°¢f"†öö²Ò6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚’æf—6†–æs°¢–b††öö²ÒçVÆÂ’†öö²æF—66&B‚“°¢Ò“° ¢–b‚fÆöF–ær’°¢F‡&÷ræWr76W'F–öäW'&÷"‚'F†R&ö&&W"æWfW"&V6†VBF†RvFW"Â6òF†W&Rv2æò&—FR7VRFò ¢²&v—fRWFôf—6ƒ²F†R66Væ&–ò—2'&ö¶VâÂæ÷BF†RÖöGVÆRâF†R†öö²VæFVB"²&ö&&W ¢²"Âv—F‚F†RÆ–W"B"²7F'B²"æBvFW"g&öÒƒÒ"²‡ööÂævWE‚‚’Òr¢²"FòƒÒ"²‡ööÂævWE‚‚’²r’“°¢Ğ¢–b‚&VVÆVB’°¢òòGvòF–ffW&VçBf–ÇW&W2W6VBFò&öGV6RF†R6ÖRÖW76vRÂæBFVÆÆ–ærF†VÒ'B—0¢òòF†RF–ffW&Væ6R&WGvVVâÖöGVÆR'VræB66Væ&–òF†BæWfW"FVÆ—fW&VB—G27VRà¢–b†FVWW7BâÖÖRæ×&†¶âævÆ&†6²ç6W'f–6W2ä&ö&&W$&—FRäDTdTÅEõD…$U4„ôÄB’°¢F‡&÷ræWr76W'F–öäW'&÷"‚'F†R6Æ–VçBæWfW"6rF†R&ö&&W"VÆÆVBVæFW"ÒF†R ¢²'7FVWW7BFW66VçB—Bö'6W'fVBv2"²FVWW7B²"&Æö6·2W"F–6²v–ç7B ¢²&F‡&W6†öÆBöbÒ"²ÖRæ×&†¶âævÆ&†6²ç6W'f–6W2ä&ö&&W$&—FRäDTdTÅEõD…$U4„ôÄ@¢²"Ò6òWFôf—6‚v2æWfW"v—fVâF†R7VRâF†R66Væ&–ò—2'&ö¶VâÂæ÷BF†R ¢²&ÖöGVÆS¢F†R6W'fW"×6–FRVÆÂ—2æ÷B&V6†–ærF†R6Æ–VçBw26÷’öbF†R†öö²â"“°¢Ğ¢F‡&÷ræWr76W'F–öäW'&÷"‚$WFôf—6‚ÆVgBF†R6ÖR†öö²÷WBÇF†÷Vv‚F†R6Æ–VçB6rF†R ¢²&&ö&&W"VÆÆVBVæFW"B"²FVWW7B²"&Æö6·2W"F–6²Âv†–6‚—2F†RöæR7VR ¢²&—B&VVÇ2–âöâ"“°¢Ğ¢ÄôttU"æ–æfò‚"WFôf—6‚67BÂF†Vâ&VVÆVB–âv†VâF†R&ö&&W"vVçBVæFW"B·Ò"÷B"ÂFVWW7B“°¢Ğ ¢ò¢ ¢¢F†R…TBG&vâBF–ffW&VçB6—¦RÂv†–6‚—2F†RöæRF†–ær66ÆR6öçG&öÂ†2Fò7GVÆÇ’Fòà¢ ¢¢ÇäÖV7W&VB÷fW"F†RF÷ÖÆVgB6÷&æW"&F†W"F†âF†RÖ–FFÆR&æBF†R÷F†W"&VæFW"66Væ&–÷0¢¢W6RÂ&V6W6RF†B—2v†W&RF†R'&æF–ærv–FvWBG&w2æBF†RÖ–FFÆR&æBW†6ÇVFW2—Bâæ'&÷p¢¢öâW'÷6S¢v–FW"&æBF¶W2–âv–FvWG2v†÷6RFW‡B6†ævW2'’—G6VÆb(	Bg&ÖR6÷VçFW"Â¢¢–ærf–wW&R(	BæBF†V—"æö—6R—2–æF—7F–æwV—6†&ÆRg&öÒ6—¦R6†ævRà¢ ¢¢Çå6WBF‡&÷Vv‚´6öFRF†VÖU6W'f–6Rç&Wf–WwÒÂF†R6ÖR6ÆÂF†R6Æ–FW"Ö¶W2Â6òF†—26÷fW'2F†P¢¢F‚g&öÒF†R7F÷&VBF†VÖRFòF†RÆ–÷WB&F†W"F†âö¶–ærF†RÆ–÷WBF—&V7FÇ’à¢¢ğ¢&—fFRfö–B‡VE66ÆR„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂFW7E6–ævÆWÆ–W$6öçFW‡B6–ævÆWÆ–W"’°¢Ö÷fUF†W&R†6öçFW‡BÂ6–ævÆWÆ–W"Â66VæT&6Ræöfg6WBƒÂÂC#’“°¢6–ævÆWÆ–W"ævWE6W'fW"‚’ç'Väöå6W'fW"‡6W'fW"Óâ°¢6ÆV$VçF—F–W2‡6–ævÆWÆ–W"ævWD6öææV7F–öâ‚’ævWE6W'fW%Æ–W"‚’æÆWfVÂ‚’“°¢Ò“°¢6öçFW‡Bç'Väöä6Æ–VçB†6Æ–VçBÓâ6Æ–VçBæ÷F–öç2æ6Æ÷VE7FGW2‚’ç6WB†æWBæÖ–æV7&gBæ6Æ–VçBä6Æ÷VE7FGW2äôdb’“°¢òòWBV×G’6·’âF†R&Wf–÷W266Væ&–òÆVfW2F†R6ÖW&ævÆVBF÷vâBööÂÂæBvFW ¢òòæ–ÖFW2Â6òF†R6÷&æW"&V–ærÖV7W&VB†BÖ÷f–ær–7GW&R&V†–æB—Bà¢6öçFW‡BævWD–çWB‚’æÆöö´BƒãbÂÓCãb“°¢6öçFW‡Bçv—EF–6·2ƒC“° ¢òòF†R…TBæ–ÖFW2—G26öÆ÷W'26öçF–çV÷W6Ç’Âv†–6‚6†÷vVBW2æö—6RfÆö÷"öb“cB6†ævV@¢òò—†VÇ2&WGvVVâç’Gvòg&ÖW2†÷vWfW"f"'BÒÖ÷f–ær–7GW&R&F†W"F†â¦—GFW"à¢òò7v—F6†VBöfb†W&RæB&6²öâBF†RVæBÂ6òF†—266Væ&–òÆVfW2F†RF†VÖR2—Bf÷VæB—Bà¢7F–ÆÅF†VÖR†6öçFW‡BÂG'VR“° ¢f–æÂ–çBv–æF÷rÒc°¢6WD‡VE66ÆR†6öçFW‡BÂÖRæ×&†¶âævÆ&†6²ç6W'f–6W2ä‡VE66ÆRäDTdTÅB“°¢¦fææ–òæf–ÆRåF‚f—'7BÒ6öçFW‡BçF¶U67&VVç6†÷B‚&‡VB×66ÆRÓ"“°¢6öçFW‡Bçv—EF–6·2‡v–æF÷r“°¢¦fææ–òæf–ÆRåF‚6V6öæBÒ6öçFW‡BçF¶U67&VVç6†÷B‚&‡VB×66ÆRÓ""“°¢–çBæö—6RÒg&ÖW2æ6†ævVE—†VÇ2†f—'7BÂ6V6öæBÂ%$äEõDõÂ%$äEô$õEDôÒÂãÂ%$äEõ$”t…B“° ¢6WD‡VE66ÆR†6öçFW‡BÂãb“°¢6öçFW‡Bçv—EF–6·2‡v–æF÷r“°¢¦fææ–òæf–ÆRåF‚Æ&vW"Ò6öçFW‡BçF¶U67&VVç6†÷B‚&‡VB×66ÆRÓ2"“°¢–çB6–væÂÒg&ÖW2æ6†ævVE—†VÇ2‡6V6öæBÂÆ&vW"Â%$äEõDõÂ%$äEô$õEDôÒÂãÂ%$äEõ$”t…B“°¢F÷V&ÆRÆ–VBÒ6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓâvÆ$†6´6Æ–VçBä…TEôÄ”õUBç66ÆR‚’“°¢6WD‡VE66ÆR†6öçFW‡BÂÖRæ×&†¶âævÆ&†6²ç6W'f–6W2ä‡VE66ÆRäDTdTÅB“°¢F÷V&ÆR&W7F÷&VBÒ6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓâvÆ$†6´6Æ–VçBä…TEôÄ”õUBç66ÆR‚’“° ¢7F–ÆÅF†VÖR†6öçFW‡BÂfÇ6R“°¢ÄôttU"æ–æfò‚"…TB66ÆR—†VÇ3¢æö—6S×·Ò6–væÃ×·Ò"Âæö—6RÂ6–væÂ“°¢–b†Æ–VBÒãb’°¢F‡&÷ræWr76W'F–öäW'&÷"‚'F†RF†VÖRw2…TB66ÆRöbãb&V6†VBF†RÆ–÷WB2"²Æ–V@¢²"Â6òæ÷F†–ær&VÆ÷rÖV7W&W266ÆVB…TB"“°¢Ğ¢–b‡&W7F÷&VBÒÖRæ×&†¶âævÆ&†6²ç6W'f–6W2ä‡VE66ÆRäDTdTÅB’°¢F‡&÷ræWr76W'F–öäW'&÷"‚'F†R…TB66ÆR7F–VBB"²&W7F÷&VB²"gFW"&V–ær6WB&6²"“°¢Ğ¢76W'DG&Wr‚$…TB66ÆR"Â&…TBç’F–ffW&VçB–â6—¦R"Âæö—6RÂ6–væÂ“°¢Ğ ¢ò¢¢F†R6÷&æW"F†R'&æF–ærv–FvWBö67W–W2Â2g&7F–öâöbF†Rg&ÖRâ¢ğ¢&—fFR7FF–2f–æÂF÷V&ÆR%$äEõDõÒãÂ%$äEô$õEDôÒÒãbÂ%$äEõ$”t…BÒã3S° ¢&—fFR7FF–2fö–B6WD‡VE66ÆR„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂF÷V&ÆR66ÆR’°¢6öçFW‡Bç'Väöä6Æ–VçB†6Æ–VçBÓâ°¢f"6W'f–6RÒÖRæ×&†¶âævÆ&†6²ç6W'f–6W2ä6Æ–VçE6W'f–6W2ç&WV—&R€¢ÖRæ×&†¶âævÆ&†6²ç6W'f–6W2åF†VÖU6W'f–6Ræ6Æ72“°¢f"F†VÖRÒ6W'f–6Ræ6÷’‚“°¢F†VÖRæ‡VE66ÆRÒ66ÆS°¢6W'f–6Rç&Wf–Wr‡F†VÖR“°¢Ò“°¢Ğ ¢ò¢ ¢¢F†R6Æ–6´uT’w26FVv÷'’G&ç6—F–öâÂG&—fVâ'’&VÂ6Æ–6²öâ&VÂ'WGFöâà¢ ¢¢Çä76W'FVBöâF†R7G&—Rw2÷6—F–öâ&F†W"F†âöâ—†VÇ3¢v†BF†RG&ç6—F–öâFöW2—2Ö÷fP¢¢6öÖWF†–ærg&öÒöæR&÷rFòæ÷F†W"ÂæB&VF–ærv†W&R—B—26—2F†BF—&V7FÇ’âF†R6öçG&öÂ—0¢¢F†RF†VÖRw2÷vâ&VGV6VBÖÖ÷F–öâ7v—F6‚(	Bv—F‚—BöâF†R7G&—R—2B—G2FW7F–æF–öâöâF†P¢¢f—'7Bg&ÖRÂv†–6‚—2F†Rv†öÆR&öÖ—6RöbF†B6WGF–ærà¢ ¢¢ÇåF†Ræ–ÖF–öâ—26Æ÷vVBf÷"F†RÖV7W&VÖVçBÂæ÷B6†÷'FVæVC¢BF†RFVfVÇB7VVB—BÆ7G0¢¢&÷WBF‡&VRæB†ÆbF–6·2Âv†–6‚—2Föò6Æ÷6RFòF†R6×Æ–ær–çFW'fÂFò&VB&VÆ–&Ç’à¢¢ğ¢&—fFRfö–B6Æ–6´wV•G&ç6—F–öâ„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡B’°¢–çBF&vWBÒ3°¢–çBG&fVÆÆVBÒ6FVv÷'•7G&—UG&fVÂ†6öçFW‡BÂF&vWBÂG'VR“°¢–çB7F–ÆÂÒ6FVv÷'•7G&—UG&fVÂ†6öçFW‡BÂF&vWBÂfÇ6R“° ¢òò—BG&—fW2Ö÷F–öâöâæBöfbFòÖ¶R—G2ö–çBÂ6ò—B†æG2F†RF†VÖR&6²Væ6†ævVBà¢7F–ÆÅF†VÖR†6öçFW‡BÂfÇ6R“° ¢–b‡G&fVÆÆVBÃÒ’°¢F‡&÷ræWr76W'F–öäW'&÷"‚'F†R6Æ–6´uT’w26FVv÷'’G&ç6—F–öâv2Ç&VG’f–æ—6†VBöâF†R ¢²&f—'7Bg&ÖRv—F‚Ö÷F–öâöâÂ6òæ÷F†–ærv2æ–ÖFVB"“°¢Ğ¢–b‡7F–ÆÂÒ’°¢F‡&÷ræWr76W'F–öäW'&÷"‚'F†R6Æ–6´uT’G&ç6—F–öâ7F–ÆÂ†B"²7F–ÆÂ²"Fò'Vâv—F‚F†R ¢²'F†VÖRw2&VGV6VBÖ÷F–öâöã²F†B7v—F6‚—27W÷6VBFòÆVfRæ÷F†–æræ–ÖF–ær"“°¢Ğ¢ÄôttU"æ–æfò‚"6Æ–6´uT’æ–ÖFVB—G26FVv÷'’6†ævR‡·ÒFòvòF–6²–â’ÂæBæ÷BBÆÂ ¢²'v—F‚&VGV6VBÖ÷F–öâ"ÂG&fVÆÆVB“°¢Ğ ¢ò¢ ¢¢÷Vç2F†R6Æ–6´uT’Â6Æ–6·26FVv÷'’ÂæB&W÷'G2†÷rf"F†R7G&—R7F–ÆÂ†BFòG&fVÂöæP¢¢F–6²ÆFW"à¢ ¢¢&WGW&â†÷rf"F†RG&ç6—F–öâ7F–ÆÂ†BFò'VâF–6²gFW"F†R6Æ–6³¢F†R7G&—Rw2&VÖ–æ–æp¢¢G&fVÂ–â—†VÇ2ÇW2F†R6öçFVçBfV–Â–â‡VæG&VGF‡2Â6òöæRçVÖ&W"6÷fW'2&÷F€¢¢ğ¢&—fFR–çB6FVv÷'•7G&—UG&fVÂ„6Æ–VçDvÖUFW7D6öçFW‡B6öçFW‡BÂ–çB6FVv÷'’Â&ööÆVâÖ÷F–öâ’°¢6öçFW‡Bç'Väöä6Æ–VçB†6Æ–VçBÓâ°¢f"6W'f–6RÒÖRæ×&†¶âævÆ&†6²ç6W'f–6W2ä6Æ–VçE6W'f–6W2ç&WV—&R€¢ÖRæ×&†¶âævÆ&†6²ç6W'f–6W2åF†VÖU6W'f–6Ræ6Æ72“°¢f"F†VÖRÒ6W'f–6Ræ6÷’‚“°¢F†VÖRçV”æ–ÖF–öç2ÒÖ÷F–öã°¢F†VÖRç&VGV6VDÖ÷F–öâÒÖ÷F–öã°¢òòV'FW"7VVC¢ÆöærVæ÷Vv‚F†BF–6²öb6×Æ–ærÆæG2–ç6–FRF†RG&ç6—F–öâà¢F†VÖRææ–ÖF–öå7VVBÒã#S°¢6W'f–6Rç&Wf–Wr‡F†VÖR“°¢6Æ–VçBæwV’ç6WE67&VVâ†æWrÖRæ×&†¶âævÆ&†6²çV’ä6Æ–6´wV•67&VVâ‚’“°¢Ò“°¢6öçFW‡Bçv—EF–6·2ƒ“° ¢òòv†W&RF†R6FVv÷'’w2÷vâ'WGFöâ—2Â–âv–æF÷r—†VÇ3¢F†R67&VVâ—2G&vâBF†RuT’66ÆRà¢F÷V&ÆUµÒ7÷BÒ6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓâ°¢f"67&VVâÒ†ÖRæ×&†¶âævÆ&†6²çV’ä6Æ–6´wV•67&VVâ’6Æ–VçBæwV’ç67&VVâ‚“°¢F÷V&ÆR66ÆRÒ6Æ–VçBævWEv–æF÷r‚’ævWDwV•66ÆR‚“°¢&WGW&âæWrF÷V&ÆUµ×³S¢66ÆRÀ¢‡67&VVâæ6FVv÷'•&÷u’†6FVv÷'’’²67&VVâæ6FVv÷'•&÷t†V–v‡B‚’ò"ã’¢66ÆWÓ°¢Ò“°¢6öçFW‡BævWD–çWB‚’ç6WD7W'6÷%÷2‡7÷E³ÒÂ7÷E³Ò“°¢6öçFW‡Bçv—EF–6·2ƒ"“°¢6öçFW‡BævWD–çWB‚’ç&W74Ö÷W6Rƒ“°¢6öçFW‡Bçv—EF–6·2ƒ“° ¢–çB&VÖ–æ–ærÒ6öçFW‡Bæ6ö×WFTöä6Æ–VçB†6Æ–VçBÓâ°¢–b‚†6Æ–VçBæwV’ç67&VVâ‚’–ç7Fæ6VöbÖRæ×&†¶âævÆ&†6²çV’ä6Æ–6´wV•67&VVâ67&VVâ’’&WGW&âÓ°¢òòF†RfV–Â6÷fW'2F†R6ÖRG&ç6—F–öâg&öÒF†R÷F†W"VæC¢F†R6öçFVçBfF–ærWöà¢òò÷VâÂöâ6FVv÷'’æBöâvRâ6÷VçFVB–âF†R6ÖRVæ—G26òöæRçVÖ&W"6'&–W0¢òò&÷F‚Ò‡VæG&VGF‚öbF†RfV–Â—2—†VÂw2v÷'F‚öbÖ÷fVÖVçBà¢&WGW&âÖF‚æ'2‡67&VVâæ6FVv÷'•&÷u’†6FVv÷'’’Ò67&VVâç7G&—U’‚’¢²†–çB’ÖF‚ç&÷VæB‡67&VVâæ6öçFVçEfV–Â‚’¢“°¢Ò“°¢6öçFW‡Bç'Väöä6Æ–VçB†6Æ–VçBÓâ6Æ–VçBæwV’ç6WE67&VVâ†çVÆÂ’“°¢6öçFW‡Bçv—EF–6·2ƒR“°¢–b‡&VÖ–æ–ærÂ’°¢F‡&÷ræWr76W'F–öäW'&÷"‚&6Æ–6¶–ærF†R6FVv÷'’B"²7÷E³Ò²"F–Bæ÷BÆVfRF†R ¢²$6Æ–6´uT’÷VâÂ6òF†W&Rv2æò7G&—RFòÖV7W&S²F†R66Væ&–ò—2'&ö¶Vâ"“°¢Ğ¢&WGW&â&VÖ–æ–æs°¢Ğ ¢ò¢ ¢¢F†R6ÖW&ÆVfW2æBF†RÆ–W"FöW2æ÷BÂv†–6‚—2F†Rv†öÆRöbv†Bg&VV6Ò&öÖ—6W2à¢ ¢¢ÇåF‡&VRF†–æw2&R76W'FVB&V6W6RÆÂF‡&VR6â'&V²–æFWVæFVçFÇ“¢F†R6ÖW&&VÆÇ¢¢FWF6†W2æBÖ÷fW2VæFW"F†RÖ÷fVÖVçB¶W—2ÂF†R&öG’7F—2W†7FÇ’v†W&R—Bv2ÂæBF†P¢¢6ÖW&—2†æFVB&6²FòF†RÆ–W"v†VâF†RÖöGVÆR—27v—F6†VBöfbâF†BÆ7BöæR—2F†P¢¢V–WBf–ÇW&R(	B6Æ–VçBÆVgBÆöö¶–ærF‡&÷Vv‚â&Ö÷W"7FZŠW«®Šğ®+b-jwZ­Ú.¶›­º$zzb¥æÚ±î¸Â¸­yêë¢°k¢G§¦*^nd after Freecam is gone.
     *
     * <p>The distance limit is checked here too, with a short radius so a few seconds of holding a
     * key is enough to reach it.
     */
    private void freecam(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        final double radius = 8.0;
        moveThere(context, singleplayer, sceneBase.offset(0, 0, 450));
        configure(context, "Freecam", module -> {
            module.settings.setSetting("maxDistance", radius);
            // Fast enough to reach the limit inside the window, still eased rather than teleporting.
            module.settings.setSetting("speed", 1.5);
        });

        Vec3 body = position(context);
        assertNotYet(context, client -> client.getCameraEntity() != client.player,
                "the camera was already detached from the player before Freecam ran");

        toggle(context, "Freecam", true);
        context.waitTicks(5);
        boolean detached = context.computeOnClient(client ->
                client.getCameraEntity() != null && client.getCameraEntity() != client.player);

        // Strafe, which a forward-only check cannot see. The axis was inverted - the module built
        // its strafe as right-minus-left while using vanilla's own rotation formula, and vanilla's
        // strafe impulse is positive for LEFT - so A and D flew the camera the opposite way to the
        // key held. Held before the forward leg so the distance limit is nowhere near reached.
        Vec3 beforeStrafe = cameraPosition(context);
        float facing = context.computeOnClient(client -> client.player.getYRot());
        context.getInput().holdKeyFor(options -> options.keyRight, 30);
        context.waitTicks(10);
        Vec3 strafed = cameraPosition(context).subtract(beforeStrafe);
        double facingRadians = Math.toRadians(facing);
        // Right of the player's facing: forward is (-sin, cos), so right is (-cos, -sin).
        double alongRight = strafed.x * -Math.cos(facingRadians) + strafed.z * -Math.sin(facingRadians);
        if (alongRight < 0.5) {
            throw new AssertionError("holding the right key moved the camera " + strafed
                    + ", which is " + String.format(java.util.Locale.ROOT, "%.2f", alongRight)
                    + " blocks to the player's right - " + (alongRight < -0.5
                    ? "it flew left instead, so the strafe axis is inverted"
                    : "it barely moved sideways at all"));
        }

        context.getInput().holdKeyFor(options -> options.keyUp, 80);
        context.waitTicks(10);
        double travelled = context.computeOnClient(client ->
                AgalarHackClient.moduleManager.getModule("Freecam")
                        instanceof me.mrhakan.agalarhack.module.render.Freecam freecam
                        ? freecam.distanceFromBody() : -1.0);
        Vec3 bodyAfter = position(context);

        // What the renderer actually uses. Camera.setup takes the entity's interpolated position,
        // and Entity.getPosition(partialTick) lerps from xo - which only a ticked entity updates.
        // This armour stand is never added to the level, so if nothing keeps xo in step the drawn
        // camera sits somewhere between where it is and where it once was, every frame.
        double interpolationGap = context.computeOnClient(client -> {
            var entity = client.getCameraEntity();
            return entity == null ? 0.0 : entity.getPosition(0.0f).distanceTo(entity.position());
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
     * was built for and had never been exercised by â€” a caller that asks once and needs the aim held
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
     * <p>The danger the bridge exists to avoid is one linµ¨¥zºè¯
â¶)à²Ö§uªİ¢ëiºĞk¢G§¦*^e long: sending {@code #goto 100 64 -200}
     * as a chat message puts a player's base coordinates in front of the whole server the moment
     * Baritone is missing or its prefix is off. So the assertion is not only that the player is told
     * something useful â€” it is that <em>no</em> line beginning with the Baritone prefix ever reached
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

        LOGGER.info("  AutoGrind planner counted existing resources without starting execution");
        grindExecutesNearbyLogs(context, singleplayer);
        grindExecutesBasicResources(context, singleplayer);
        grindExecutesRecipesAndSmelting(context, singleplayer);
    }

    /** A real planner-to-world-to-inventory run, then an inventory-based restart after interruption. */
    private void grindExecutesNearbyLogs(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        BlockPos base = sceneBase.offset(0, 0, 510);
        BlockPos first = base.offset(0, 1, 3);
        BlockPos second = base.offset(2, 1, 3);
        moveThere(context, singleplayer, base);
        setInventory(singleplayer, slots -> { });
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = siµ¨¥zºè¯
â¶)à²Ö§uªİ¢ëiºĞk¢G§¦*^ngleplayer.getConnection().getServerPlayer();
            player.setGameMode(GameType.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            player.setDeltaMovement(Vec3.ZERO);
            player.getInventory().setItem(40, Items.OAK_LOG.getDefaultInstance());
            player.containerMenu.broadcastChanges();
            player.level().setBlockAndUpdate(first, Blocks.OAK_LOG.defaultBlockState());
            player.level().setBlockAndUpdate(second, Blocks.OAK_LOG.defaultBlockState());
        });
        singleplayer.getConnection().waitForChunksRender();
        context.waitTicks(10);
        if (context.computeOnClient(client -> countItem(client, Items.OAK_LOG)) != 1) {
            throw new AssertionError("AutoGrind setup did not synchronize the one starting log in the offhand");
        }
        if (!context.computeOnClient(client -> client.level.getBlockState(first).is(Blocks.OAK_LOG)
                && client.level.getBlockState(second).is(Blocks.OAK_LOG))) {
            throw new AssertionError("AutoGrind setup did not load both test logs into the client world");
        }
        // Start with the view pointed away so the scenario proves AutoGrind rotates before it
        // checks line of sight and invokes vanilla block breaking.
        context.getInput().lookAt(base.offset(0, 1, -3));
        context.waitTicks(20);
        float originalYaw = context.computeOnClient(client -> client.player.getYRot());
        float originalPitch = context.computeOnClient(client -> client.player.getXRot());

        context.runOnClient(client -> me.mrhakan.agalarhack.managers.CommandManager.handleChat(
                me.mrhakan.agalarhack.AgalarHackClient.prefix + "grind run log 3"));
        boolean waitingForFirstDrop = settle(context, client -> me.mrhakan.agalarhack.services.ClientServices
                .require(me.mrhakan.agalarhack.services.GrindExecutor.class).state()
                == me.mrhakan.agalarhack.services.TaskRunner.State.NEEDS_MOVEMENT, 240);
        if (!waitingForFirstDrop) {
            String diagnostics = context.computeOnClient(client -> {
                var executor = me.mrhakan.agalarhack.services.ClientServices.require(
                        me.mrhakan.agalarhack.services.GrindExecutor.class);
                var scanners = me.mrhakan.agalarhack.services.ClientServices.require(
                        me.mrhakan.agalarhack.services.ScannerService.class);
                return "state=" + executor.state() + " task=" + executor.currentTask()
                        + " blocked=" + executor.blockedReason() + " scan=" + scanners.lastUsage()
                        + " logs=" + countItem(client, Items.OAK_LOG)
                        + " first=" + client.level.getBlockState(first)
                        + " second=" + client.level.getBlockState(second)
                        + " position=" + client.player.position()
                        + " view=" + client.player.getYRot() + "/" + client.player.getXRot();
            });
            throw new AssertionError("AutoGrind did not pause for the nearby broken log's uncollected drop; "
                    + diagnostics);
        }
        String firstDropReason = context.computeOnClient(client ->
                me.mrhakan.agalarhack.services.ClientServices.require(
                        me.mrhakan.agalarhack.services.GrindExecutor.class).blockedReason());
        if (context.computeOnClient(client -> countItem(client, Items.OAK_LOG)) != 1
                || !context.computeOnClient(client -> client.level.getBlockState(first).isAir())
                || !context.computeOnClient(client -> client.level.getBlockState(second).is(Blocks.OAK_LOG))
                || firstDropReason == null || !firstDropReason.contains("log drop at")) {
            throw new AssertionError("AutoGrind did not wait for the first real drop before starting another "
                    + "log: inventory=" + context.computeOnClient(client -> countItem(client, Items.OAK_LOG))
                    + " first=" + context.computeOnClient(client -> client.level.getBlockState(first))
                    + " second=" + context.computeOnClient(client -> client.level.getBlockState(second))
                    + " reason=" + firstDropReason);
        }

        // The block is three blocks away, so its drop is outside pickup reach. Move onto the actual
        // world drop, resume the task, then stop and start again to prove it recounts inventory.
        moveToDroppedLog(context, singleplayer, first);
        context.runOnClient(client -> me.mrhakan.agalarhack.managers.CommandManager.handleChat(
                me.mrhakan.agalarhack.AgalarHackClient.prefix + "grind resume"));
        boolean firstCollected = settle(context, client -> countItem(client, Items.OAK_LOG) >= 2, 120);
        if (!firstCollected) {
            throw new AssertionError("AutoGrind did not recognize the first log after moving onto its drop");
        }
        context.runOnClient(client -> me.mrhakan.agalarhack.managers.CommandManager.handleChat(
                me.mrhakan.agalarhack.AgalarHackClient.prefix + "grind stop"));
        context.waitTicks(2);
        context.runOnClient(client -> me.mrhakan.agalarhack.managers.CommandManager.handleChat(
                me.mrhakan.agalarhack.AgalarHackClient.prefix + "grind run log 3"));
        boolean secondGatherResolved = settle(context, client -> {
            int carriedLogs = countItem(client, Items.OAK_LOG);
            var state = me.mrhakan.agalarhack.services.ClientServices.require(
                    me.mrhakan.agalarhack.services.GrindExecutor.class).state();
            return client.level.getBlockState(second).isAir()
                    && ((state == me.mrhakan.agalarhack.services.TaskRunner.State.NEEDS_MOVEMENT
                            && carriedLogs == 2)
                    || (state == me.mrhakan.agalarhack.services.TaskRunner.State.DONE
                            && carriedLogs == 3));
        }, 240);
        var restartedState = context.computeOnClient(client ->
                me.mrhakan.agalarhack.services.ClientServices.require(
                        me.mrhakan.agalarhack.services.GrindExecutor.class).state());
        int restartedLogs = context.computeOnClient(client -> countItem(client, Items.OAK_LOG));
        if (!secondGatherResolved || !context.computeOnClient(client -> client.level.getBlockState(second).isAir())
                || (restartedState == me.mrhakan.agalarhack.services.TaskRunner.State.NEEDS_MOVEMENT
                        && restartedLogs != 2)
                || (restartedState == me.mrhakan.agalarhack.services.TaskRunner.State.DONE
                        && restartedLogs != 3)) {
            throw new AssertionError("AutoGrind did not restart from two carried logs and break exactly one more; "
                    + "inventory=" + restartedLogs
                    + " first=" + context.computeOnClient(client -> client.level.getBlockState(first))
                    + " second=" + context.computeOnClient(client -> client.level.getBlockState(second))
                    + " state=" + restartedState);
        }
        if (restartedState == me.mrhakan.agalarhack.services.TaskRunner.State.NEEDS_MOVEMENT) {
            moveToDroppedLog(context, singleplayer, second);
            context.runOnClient(client -> me.mrhakan.agalarhack.managers.CommandManager.handleChat(
                    me.mrhakan.agalarhack.AgalarHackClient.prefix + "grind resume"));
        }
        boolean complete = settle(context, client -> countItem(client, Items.OAK_LOG) == 3
                && me.mrhakan.agalarhack.services.ClientServices.require(
                        me.mrhakan.agalarhack.services.GrindExecutor.class).state()
                        == me.mrhakan.agalarhack.services.TaskRunner.State.DONE, 120);
        if (!complete) throw new AssertionError("AutoGrind did not finish after its final drop reached inventory");
        context.waitTicks(2);
        var state = context.computeOnClient(client ->
                me.mrhakan.agalarhack.services.ClientServices.require(
                        me.mrhakan.agalarhack.services.GrindExecutor.class).state());
        if (state != me.mrhakan.agalarhack.services.TaskRunner.State.DONE) {
            throw new AssertionError("AutoGrind changed both blocks and received both drops but did not "
                    + "complete its task: " + state);
        }
        float finalYaw = context.computeOnClient(client -> client.player.getYRot());
        float finalPitch = context.computeOnClient(client -> client.player.getXRot());
        if (Math.abs(net.minecraft.util.Mth.wrapDegrees(finalYaw - originalYaw)) > 0.01f
                || Math.abs(finalPitch - originalPitch) > 0.01f) {
            throw new AssertionError("AutoGrind did not release its temporary aim rotation: original="
                    + originalYaw + "/" + originalPitch + " final=" + finalYaw + "/" + finalPitch);
        }
        LOGGER.info("  AutoGrind broke nearby logs, confirmed drops, and resumed from inventory");
        grindReportsMovementForDistantLogs(context, singleplayer);
    }

    /** A visible target outside vanilla reach pauses honestly, then resumes after manual movement. */
    private void grindReportsMovementForDistantLogs(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        BlockPos base = sceneBase.offset(0, 0, 540);
        BlockPos target = base.offset(6, 1, 0);
        moveThere(context, singleplayer, base);
        setInventory(singleplayer, slots -> { });
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            player.setGameMode(GameType.SURVIVAL);
            player.setDeltaMovement(Vec3.ZERO);
            player.level().setBlockAndUpdate(target, Blocks.OAK_LOG.defaultBlockState());
        });
        singleplayer.getConnection().waitForChunksRender();
        context.waitTicks(10);

        context.runOnClient(client -> me.mrhakan.agalarhack.managers.CommandManager.handleChat(
                me.mrhakan.agalarhack.AgalarHackClient.prefix + "grind run log 1"));
        boolean waitingForMovement = settle(context, client ->
                me.mrhakan.agalarhack.services.ClientServices.require(
                        me.mrhakan.agalarhack.services.GrindExecutor.class).state()
                        == me.mrhakan.agalarhack.services.TaskRunner.State.NEEDS_MOVEMENT, 160);
        String blocked = context.computeOnClient(client ->
                me.mrhakan.agalarhack.services.ClientServices.require(
                        me.mrhakan.agalarhack.services.GrindExecutor.class).blockedReason());
        if (!waitingForMovement || blocked == null
                || !blocked.contains(target.getX() + " " + target.getY() + " " + target.getZ())
                || !blocked.contains("movement automation unavailable")) {
            throw new AssertionError("AutoGrind did not expose the distant target and unavailable movement: "
                    + blocked);
        }
        if (context.computeOnClient(client -> !client.level.getBlockState(target).is(Blocks.OAK_LOG))
                || context.computeOnClient(client -> countItem(client, Items.OAK_LOG)) != 0) {
            throw new AssertionError("AutoGrind claimed to handle a distant log before the player moved");
        }

        moveThere(context, singleplayer, base.offset(3, 0, 0));
        context.runOnClient(client -> me.mrhakan.agalarhack.managers.CommandManager.handleChat(
                me.mrhakan.agalarhack.AgalarHackClient.prefix + "grind resume"));
        boolean dropPaused = settle(context, client -> client.level.getBlockState(target).isAir()
                && me.mrhakan.agalarhack.services.ClientServices.require(
                        me.mrhakan.agalarhack.services.GrindExecutor.class).state()
                        == me.mrhakan.agalarhack.services.TaskRunner.State.NEEDS_MOVEMENT, 240);
        if (!dropPaused || context.computeOnClient(client -> countItem(client, Items.OAK_LOG)) != 0) {
            throw new AssertionError("AutoGrind did not wait for the distant target's real drop; target="
                    + context.computeOnClient(client -> client.level.getBlockState(target))
                    + " inventory=" + context.computeOnClient(client -> countItem(client, Items.OAK_LOG))
                    + " reason=" + context.computeOnClient(client -> me.mrhakan.agalarhack.services.ClientServices
                            .require(me.mrhakan.agalarhack.services.GrindExecutor.class).blockedReason()));
        }
        moveToDroppedLog(context, singleplayer, target);
        context.runOnClient(client -> me.mrhakan.agalarhack.managers.CommandManager.handleChat(
                me.mrhakan.agalarhack.AgalarHackClient.prefix + "grind resume"));
        boolean complete = settle(context, client -> countItem(client, Items.OAK_LOG) == 1
                && me.mrhakan.agalarhack.services.ClientServices.require(
                        me.mrhakan.agalarhack.services.GrindExecutor.class).state()
                        == me.mrhakan.agalarhack.services.TaskRunner.State.DONE, 120);
        if (!complete) throw new AssertionError("AutoGrind did not resume after moving onto the dropped log");
        LOGGER.info("  AutoGrind paused honestly outside reach and resumed after manual movement");
    }

    /** Exercises the other raw-resource drop mappings through vanilla block breaking and pickup. */
    private void grindExecutesBasicResources(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        BlockPos base = sceneBase.offset(0, 0, 560);
        BlockPos stone = base.offset(0, 1, 1);
        BlockPos coalOre = base.offset(1, 1, 0);
        BlockPos ironOre = base.offset(-1, 1, 0);
        moveThere(context, singleplayer, base);
        setInventory(singleplayer, slots -> {
            slots.setItem(0, Items.WOODEN_PICKAXE.getDefaultInstance());
            slots.setSelectedSlot(0);
        });
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            player.setGameMode(GameType.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            player.level().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            player.level().setBlockAndUpdate(coalOre, Blocks.COAL_ORE.defaultBlockState());
            player.level().setBlockAndUpdate(ironOre, Blocks.IRON_ORE.defaultBlockState());
        });
        singleplayer.getConnection().waitForChunksRender();
        context.waitTicks(10);

        runGrindUntilResolved(context, "cobblestone 1", client -> countItem(client, Items.COBBLESTONE) == 1, 240);
        if (!context.computeOnClient(client -> client.level.getBlockState(stone).isAir())) {
            throw new AssertionError("AutoGrind did not break the stone block for cobblestone");
        }
        runGrindUntilResolved(context, "coal 1", client -> countItem(client, Items.COAL) == 1, 240);
        if (!context.computeOnClient(client -> client.level.getBlockState(coalOre).isAir())) {
            throw new AssertionError("AutoGrind did not break the coal ore");
        }

        setInventory(singleplayer, slots -> {
            slots.setItem(0, Items.STONE_PICKAXE.getDefaultInstance());
            slots.setSelectedSlot(0);
        });
        runGrindUntilResolved(context, "raw_iron 1", client -> countItem(client, Items.RAW_IRON) == 1, 240);
        if (!context.computeOnClient(client -> client.level.getBlockState(ironOre).isAir())) {
            throw new AssertionError("AutoGrind did not break the iron ore with a stone pickaxe");
        }
        LOGGER.info("  AutoGrind gathered cobblestone, coal, and raw iron and confirmed each drop");
    }

    /** Runs each current recipe family, including station placement and an eight-item furnace batch. */
    private void grindExecutesRecipesAndSmelting(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        BlockPos base = sceneBase.offset(0, 0, 570);
        moveThere(context, singleplayer, base);
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            player.setGameMode(GameType.SURVIVAL);
            player.setHealth(player.getMaxHealth());
        });

        setInventory(singleplayer, slots -> slots.setItem(0, new ItemStack(Items.OAK_LOG, 1)));
        runGrindUntilResolved(context, "planks 4", client -> countItem(client, Items.OAK_PLANKS) == 4, 200);

        setInventory(singleplayer, slots -> slots.setItem(0, new ItemStack(Items.OAK_PLANKS, 2)));
        runGrindUntilResolved(context, "stick 4", client -> countItem(client, Items.STICK) == 4, 200);

        setInventory(singleplayer, slots -> slots.setItem(0, new ItemStack(Items.OAK_PLANKS, 4)));
        runGrindUntilResolved(context, "crafting_table 1",
                client -> countItem(client, Items.CRAFTING_TABLE) == 1, 200);

        // A carried crafting table becomes a real nearby block before the wooden pickaxe's 3x3 craft.
        setInventory(singleplayer, slots -> {
            slots.setItem(0, new ItemStack(Items.OAK_LOG, 2));
            slots.setItem(1, Items.CRAFTING_TABLE.getDefaultInstance());
            slots.setSelectedSlot(0);
        });
        runGrindUntilResolved(context, "wooden_pickaxe 1",
                client -> countItem(client, Items.WOODEN_PICKAXE) == 1 && hasAdjacentCraftingTable(client, base), 400);

        setInventory(singleplayer, slots -> {
            slots.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
            slots.setItem(1, new ItemStack(Items.STICK, 2));
        });
        runGrindUntilResolved(context, "stone_pickaxe 1",
                client -> countItem(client, Items.STONE_PICKAXE) == 1, 300);

        setInventory(singleplayer, slots -> slots.setItem(0, new ItemStack(Items.COBBLESTONE, 8)));
        runGrindUntilResolved(context, "furnace 1", client -> countItem(client, Items.FURNACE) == 1, 300);

        setInventory(singleplayer, slots -> {
            slots.setItem(0, Items.FURNACE.getDefaultInstance());
            slots.setItem(1, new ItemStack(Items.RAW_IRON, 8));
            slots.setItem(2, Items.COAL.getDefaultInstance());
            slots.setItem(3, new ItemStack(Items.STICK, 2));
        });
        runGrindUntilResolved(context, "iron_ingot 1", client -> countItem(client, Items.IRON_INGOT) == 8, 2_200);

        runGrindUntilResolved(context, "iron_pickaxe 1",
                client -> countItem(client, Items.IRON_PICKAXE) == 1, 400);
        LOGGER.info("  AutoGrind crafted 2x2 and 3x3 recipes, placed both stations, smelted iron, and made an iron pickaxe");
    }

    private static void runGrindUntilResolved(ClientGameTestContext context, String request,
            Predicate<Minecraft> result, int budget) {
        context.runOnClient(client -> me.mrhakan.agalarhack.managers.CommandManager.handleChat(
                me.mrhakan.agalarhack.AgalarHackClient.prefix + "grind run " + request));
        boolean resolved = settle(context, client -> {
            var state = me.mrhakan.agalarhack.services.ClientServices.require(
                    me.mrhakan.agalarhack.services.GrindExecutor.class).state();
            return state == me.mrhakan.agalarhack.services.TaskRunner.State.DONE
                    || state == me.mrhakan.agalarhack.services.TaskRunner.State.FAILED
                    || state == me.mrhakan.agalarhack.services.TaskRunner.State.NEEDS_MOVEMENT;
        }, budget);
        String diagnostic = context.computeOnClient(client -> {
            var executor = me.mrhakan.agalarhack.services.ClientServices.require(
                    me.mrhakan.agalarhack.services.GrindExecutor.class);
            return "state=" + executor.state() + " task=" + executor.currentTask()
                    + " failure=" + executor.failure() + " blocked=" + executor.blockedReason();
        });
        boolean succeeded = context.computeOnClient(client ->
                me.mrhakan.agalarhack.services.ClientServices.require(
                        me.mrhakan.agalarhack.services.GrindExecutor.class).state()
                        == me.mrhakan.agalarhack.services.TaskRunner.State.DONE && result.test(client));
        if (!resolved || !succeeded) {
            throw new AssertionError("AutoGrind did not complete .grind run " + request + ": " + diagnostic);
        }
    }

    private static int countItem(Minecraft client, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            var stack = client.player.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static boolean hasAdjacentCraftingTable(Minecraft client, BlockPos base) {
        return client.level.getBlockState(base.offset(1, 0, 0)).is(Blocks.CRAFTING_TABLE)
                || client.level.getBlockState(base.offset(-1, 0, 0)).is(Blocks.CRAFTING_TABLE)
                || client.level.getBlockState(base.offset(0, 0, 1)).is(Blocks.CRAFTING_TABLE)
                || client.level.getBlockState(base.offset(0, 0, -1)).is(Blocks.CRAFTING_TABLE);
    }

    /** Moves the test player onto the actual world drop, as a manual pickup would. */
    private static void moveToDroppedLog(ClientGameTestContext context,
            TestSingleplayerContext singleplayer, BlockPos near) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayer.getConnection().getServerPlayer();
            java.util.List<Entity> present = new java.util.ArrayList<>();
            player.level().getAllEntities().forEach(present::add);
            net.minecraft.world.entity.item.ItemEntity closest = null;
            double closestDistance = Double.MAX_VALUE;
            Vec3 target = Vec3.atCenterOf(near);
            for (Entity entity : present) {
                if (entity instanceof net.minecraft.world.entity.item.ItemEntity item
                        && item.getItem().is(Items.OAK_LOG)) {
                    double distance = item.position().distanceToSqr(target);
                    if (distance < closestDistance) {
                        closest = item;
                        closestDistance = distance;
                    }
                }
            }
            if (closest == null || closestDistance > 64.0) {
                throw new AssertionError("No real oak log drop was present near " + near);
            }
            player.teleportTo(closest.getX(), closest.getY(), closest.getZ());
            player.setDeltaMovement(Vec3.ZERO);
        });
        singleplayer.getConnection().waitForChunksRender();
        context.waitTicks(10);
    }

    /**
     * An addon that the Fabric loader actually loaded, registering a module and a command.
     *
     * <p>{@link TestAddon} declares itself under the {@code agalarhack} entrypoint in the game test
     * mod's own {@code fabric.mod.json} â€” the same way a third-party addon would. So this exercises
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
            ServerPlayer player = singleplaµ¨¥Â¸­yêë¢°k¢G§¦*^yer.getConnection().getServerPlayer();
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
