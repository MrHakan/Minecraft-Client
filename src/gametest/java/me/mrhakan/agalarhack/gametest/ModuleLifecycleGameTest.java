package me.mrhakan.agalarhack.gametest;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Enables every module in a real world, lets it run, and disables it again.
 *
 * <p>This is the check unit tests cannot make. A module's {@code onEnable}, {@code onUpdate} and
 * render path only exist once there is a client, a level and a player, and a module that throws
 * there compiles fine and passes every unit test in the repository.
 *
 * <p>Failures are read off the module's own state rather than caught here, because the mod already
 * catches them: {@link me.mrhakan.agalarhack.managers.ModuleManager#tick} force-disables a module
 * whose tick throws, and {@link Module#setToggled(boolean, boolean)} reverts a module whose
 * {@code onEnable} throws. So a module that is still enabled after ticking is a module that ticked
 * without throwing, and that is exactly the claim the {@code UNTESTED} badge is about.
 */
public class ModuleLifecycleGameTest implements FabricClientGameTest {

    /**
     * Long enough for a module to do something - scanners get a scheduling slice, timers expire,
     * and the world renders several frames with the module active - and short enough that 50-odd
     * modules still finish in about a minute.
     */
    private static final int TICKS_PER_MODULE = 20;

    private static final Logger LOGGER = LoggerFactory.getLogger("agalarhack-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        // The HUD, the Control Center and the update checker all print this. It has to be the
        // 2.x.yy string exactly as written in gradle.properties: Fabric's parsed version would turn
        // 2.0.01 into 2.0.1, and a literal once left 26.2.7 announcing itself as 26.2.5.
        String version = AgalarHackClient.VERSION;
        if (!version.matches("2\\.(0|[1-9][0-9]*)\\.[0-9]{2}")) {
            throw new AssertionError("the client reports version '" + version + "', not a 2.x.yy release number");
        }
        LOGGER.info("Client reports version {}", version);
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            singleplayer.getConnection().waitForChunksRender();
            // A consistent-settings world is already flat, fixed-seed, and has time, weather and mob
            // spawning frozen, so the only thing left to arrange is something for the modules to see.
            singleplayer.getServer().runOnServer(server ->
                    TestScene.build(singleplayer.getConnection().getServerPlayer()));
            context.waitTicks(20);
            LOGGER.info("Exercising {} modules, {} ticks each", moduleNames(context).size(), TICKS_PER_MODULE);

            List<String> failures = new ArrayList<>();
            for (String name : moduleNames(context)) {
                exercise(context, name, failures);
            }
            if (!failures.isEmpty()) {
                throw new AssertionError("Modules failed in a running world:\n  " + String.join("\n  ", failures));
            }
        }
    }

    private static List<String> moduleNames(ClientGameTestContext context) {
        return context.computeOnClient(client -> AgalarHackClient.moduleManager.getModuleList()
                .stream().map(Module::getName).toList());
    }

    private void exercise(ClientGameTestContext context, String name, List<String> failures) {
        int failedBefore = failures.size();
        // persist=false throughout: the point is to prove the lifecycle runs, not to rewrite the
        // config file 53 times, and a persisted toggle would leave the next run starting differently.
        boolean enabled = context.computeOnClient(client -> {
            Module module = AgalarHackClient.moduleManager.getModule(name);
            boolean wasOn = module.isToggled();
            if (!wasOn) {
                module.setToggled(true, false);
            }
            return module.isToggled();
        });
        if (!enabled) {
            failures.add(name + ": onEnable threw (see the log for the stack trace)");
            return;
        }

        context.waitTicks(TICKS_PER_MODULE);

        boolean stillOn = context.computeOnClient(client ->
                AgalarHackClient.moduleManager.getModule(name).isToggled());
        if (!stillOn) {
            failures.add(name + ": disabled itself while ticking; a tick threw, or the module "
                    + "turned itself off within " + TICKS_PER_MODULE + " ticks");
        }

        context.runOnClient(client -> {
            Module module = AgalarHackClient.moduleManager.getModule(name);
            if (module.isToggled()) {
                module.setToggled(false, false);
            }
        });
        // A disable that throws is swallowed by setToggled, so it cannot be seen from here. The log
        // scan in ClientLogGameTest is what catches those.
        context.waitTicks(2);
        if (failures.size() == failedBefore) {
            LOGGER.info("  {} ok", name);
        }
    }
}
