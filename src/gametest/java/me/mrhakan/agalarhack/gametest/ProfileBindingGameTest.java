package me.mrhakan.agalarhack.gametest;

import me.mrhakan.agalarhack.AgalarHackClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Profile dimension bindings, driven through real world replacement.
 *
 * <p>A dimension binding is the one profile feature that only does anything during a lifecycle
 * event: nothing about it can be observed without the client's level actually being replaced, and
 * `ProfileManager.tick` is where it happens. So this drives real teleports through the server, the
 * same way {@link WorldTransitionGameTest} does, and never posts a {@code WorldChanged} event of its
 * own — a posted event would prove the handler runs, not that the client's own transition reaches it.
 *
 * <p>The marker is {@code Freecam.maxDistance}, a plain number on a module that is left switched
 * off. Three distinct values are used so that **every** auto-load is observable: each profile holds
 * its own value, and a third is set by hand before each transition, so a profile that failed to load
 * cannot be mistaken for one that loaded and happened to agree.
 *
 * <p>The first arrival in the Nether is the control. Nothing can be bound there yet — binding needs
 * the player to be in the dimension — so that arrival must change nothing, which is what makes the
 * later arrivals evidence that the binding caused the load rather than the transition alone.
 */
public final class ProfileBindingGameTest implements FabricClientGameTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("agalarhack-gametest");

    private static final String BASELINE = "ah-binding-baseline";
    private static final String NETHER_PROFILE = "ah-binding-nether";
    private static final String OVERWORLD_PROFILE = "ah-binding-overworld";
    private static final String RENAME_BEFORE = "ah-binding-rename1";
    private static final String RENAME_AFTER = "ah-binding-rename2";

    private static final String MARKER_MODULE = "Freecam";
    private static final String MARKER = "maxDistance";
    private static final double IN_OVERWORLD_PROFILE = 24.0;
    private static final double IN_NETHER_PROFILE = 99.0;
    private static final double NEITHER_PROFILE = 55.0;

    private BlockPos overworldSpot;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer =
                     context.worldBuilder().setUseConsistentSettings(true).create()) {
            singleplayer.getConnection().waitForChunksRender();
            prepare(context, singleplayer);
            bindingsFollowARenameAndGoWithADelete(context);
            theFirstArrivalHasNothingBound(context, singleplayer);
            arrivingInTheOverworldLoadsItsProfile(context, singleplayer);
            returningToTheNetherLoadsItsProfile(context, singleplayer);
            LOGGER.info("  Profile bindings passed: real Nether round trip, each arrival loading its "
                    + "own bound profile, with an unbound first arrival as the control");
        } finally {
            cleanUp(context);
        }
    }

    /** Two profiles differing only in the marker, and a baseline to put the suite back as found. */
    private void prepare(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        overworldSpot = singleplayer.getServer().computeOnServer(server ->
                singleplayer.getConnection().getServerPlayer().blockPosition());
        context.runOnClient(client -> {
            AgalarHackClient.moduleManager.getModuleList().forEach(module -> module.setToggled(false, false));
            AgalarHackClient.PROFILES.save(BASELINE);

            setMarker(IN_NETHER_PROFILE);
            AgalarHackClient.PROFILES.save(NETHER_PROFILE);
            setMarker(IN_OVERWORLD_PROFILE);
            AgalarHackClient.PROFILES.save(OVERWORLD_PROFILE);

            require(Level.OVERWORLD.equals(client.level.dimension()),
                    "this scenario has to start in the overworld");
            AgalarHackClient.PROFILES.bindCurrentDimension(client, OVERWORLD_PROFILE);
            require(OVERWORLD_PROFILE.equals(AgalarHackClient.PROFILES.getDimensionProfile(client)),
                    "the overworld binding did not take");
        });
    }

    /**
     * The maintenance paths for a binding, which is where this scenario found a real defect.
     *
     * <p>Server bindings are rewritten on rename and removed on delete. Dimension bindings arrived
     * later and were left out of both, so renaming a profile silently stopped its dimension binding
     * from ever firing again - no error, no log line, the feature simply stopped working - and
     * deleting one left a binding pointing at a profile that no longer existed, ready to come back
     * to life if a later profile reused the name.
     */
    private void bindingsFollowARenameAndGoWithADelete(ClientGameTestContext context) {
        context.runOnClient(client -> {
            AgalarHackClient.PROFILES.save(RENAME_BEFORE);
            AgalarHackClient.PROFILES.bindCurrentDimension(client, RENAME_BEFORE);
            require(RENAME_BEFORE.equals(AgalarHackClient.PROFILES.getDimensionProfile(client)),
                    "the fixture binding did not take");

            AgalarHackClient.PROFILES.rename(RENAME_BEFORE, RENAME_AFTER);
            String afterRename = AgalarHackClient.PROFILES.getDimensionProfile(client);
            require(RENAME_AFTER.equals(afterRename), "renaming a profile left its dimension binding "
                    + "pointing at \"" + afterRename + "\", a profile that no longer exists, so the "
                    + "binding silently stopped working. Server bindings are rewritten on rename; "
                    + "dimension bindings have to be too");

            AgalarHackClient.PROFILES.delete(RENAME_AFTER);
            String afterDelete = AgalarHackClient.PROFILES.getDimensionProfile(client);
            require(afterDelete == null, "deleting a profile left its dimension binding pointing at \""
                    + afterDelete + "\", so a later profile reusing that name would silently inherit "
                    + "the binding. Server bindings are removed on delete; dimension bindings have "
                    + "to be too");

            // Put the real fixture back: the phases below need the overworld bound.
            AgalarHackClient.PROFILES.bindCurrentDimension(client, OVERWORLD_PROFILE);
            setMarker(NEITHER_PROFILE);
        });
    }

    /**
     * The control. The Nether cannot be bound before the player has been there, so this arrival must
     * change nothing - otherwise the later arrivals would prove only that transitions reload
     * profiles, which is a different claim.
     */
    private void theFirstArrivalHasNothingBound(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        goTo(context, singleplayer, Level.NETHER, "execute in minecraft:the_nether run tp @a 0 130 0");
        context.waitTicks(10);
        context.runOnClient(client -> {
            require(AgalarHackClient.PROFILES.getDimensionProfile(client) == null,
                    "something was already bound to the nether, so the control proves nothing");
            require(marker(client) == NEITHER_PROFILE, "arriving in an unbound dimension changed the "
                    + "marker from " + NEITHER_PROFILE + " to " + marker(client) + ", so a transition "
                    + "reloads a profile even with nothing bound");
            AgalarHackClient.PROFILES.bindCurrentDimension(client, NETHER_PROFILE);
            setMarker(NEITHER_PROFILE);
        });
    }

    private void arrivingInTheOverworldLoadsItsProfile(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        goTo(context, singleplayer, Level.OVERWORLD, "execute in minecraft:overworld run tp @a "
                + overworldSpot.getX() + " " + overworldSpot.getY() + " " + overworldSpot.getZ());
        awaitMarker(context, IN_OVERWORLD_PROFILE, OVERWORLD_PROFILE);
        context.runOnClient(client -> setMarker(NEITHER_PROFILE));
    }

    private void returningToTheNetherLoadsItsProfile(ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        goTo(context, singleplayer, Level.NETHER, "execute in minecraft:the_nether run tp @a 0 130 0");
        awaitMarker(context, IN_NETHER_PROFILE, NETHER_PROFILE);
    }

    /** Drives a real server-side teleport and waits for the client's level to actually be replaced. */
    private void goTo(ClientGameTestContext context, TestSingleplayerContext singleplayer,
            net.minecraft.resources.ResourceKey<Level> target, String command) {
        ClientLevel before = context.computeOnClient(client -> client.level);
        singleplayer.getServer().runCommand(command);
        context.waitFor(client -> client.level != null && client.level != before
                && client.level.dimension().equals(target), 600);
    }

    /**
     * Waits for the bound profile's value rather than a fixed number of ticks: the auto-load happens
     * on a client tick after the level swap, and how many ticks that takes is not this test's claim.
     */
    private void awaitMarker(ClientGameTestContext context, double expected, String profile) {
        boolean arrived = context.computeOnClient(client -> true)
                && waitForMarker(context, expected);
        if (!arrived) {
            double seen = context.computeOnClient(ProfileBindingGameTest::marker);
            String active = context.computeOnClient(client -> AgalarHackClient.PROFILES.getActiveProfile());
            throw new AssertionError("arriving in this dimension did not load its bound profile \""
                    + profile + "\": the marker is " + seen + " rather than " + expected
                    + " and the active profile is \"" + active + "\"");
        }
        String active = context.computeOnClient(client -> AgalarHackClient.PROFILES.getActiveProfile());
        if (!profile.equals(active)) {
            throw new AssertionError("the marker reached " + expected + " but the active profile is \""
                    + active + "\" rather than \"" + profile + "\"");
        }
    }

    private boolean waitForMarker(ClientGameTestContext context, double expected) {
        for (int waited = 0; waited < 200; waited += 4) {
            context.waitTicks(4);
            if (context.computeOnClient(ProfileBindingGameTest::marker) == expected) return true;
        }
        return false;
    }

    private static double marker(Minecraft client) {
        var module = AgalarHackClient.moduleManager.getModule(MARKER_MODULE);
        Object value = module == null ? null : module.settings.getSetting(MARKER);
        return value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    private static void setMarker(double value) {
        AgalarHackClient.moduleManager.getModule(MARKER_MODULE).settings.setSetting(MARKER, value);
    }

    /**
     * Deletes the test profiles, which also makes any binding left behind inert - an auto-load
     * checks the profile still exists - and restores whatever the suite was running before.
     */
    private void cleanUp(ClientGameTestContext context) {
        context.runOnClient(client -> {
            if (client.level != null) AgalarHackClient.PROFILES.unbindCurrentDimension(client);
            if (AgalarHackClient.PROFILES.exists(BASELINE)) AgalarHackClient.PROFILES.load(BASELINE);
            for (String profile : new String[]{NETHER_PROFILE, OVERWORLD_PROFILE, RENAME_BEFORE,
                    RENAME_AFTER, BASELINE}) {
                if (AgalarHackClient.PROFILES.exists(profile)) AgalarHackClient.PROFILES.delete(profile);
            }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
