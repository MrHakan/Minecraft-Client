package me.mrhakan.agalarhack.gametest;

import java.util.ArrayList;
import java.util.List;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.render.BlockESP;
import me.mrhakan.agalarhack.module.movement.Jesus;
import me.mrhakan.agalarhack.module.movement.NoFall;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.ScannerService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real collision shapes and server block updates; no synthetic world events or pixel assertions. */
public final class RemainingSafetyGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("agalarhack-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        List<String> failures = new ArrayList<>();
        try (var world = context.worldBuilder().setUseConsistentSettings(true).create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> AgalarHackClient.moduleManager.getModuleList()
                    .forEach(module -> module.setToggled(false, false)));
            BlockPos base = world.getServer().computeOnServer(server -> {
                var player = world.getConnection().getServerPlayer();
                return new BlockPos((player.getBlockX() >> 4) * 16,
                        player.getBlockY(), (player.getBlockZ() >> 4) * 16 + 8);
            });
            surface(context, world, base, Blocks.OAK_FENCE, 1.5, "fence", failures);
            surface(context, world, base, Blocks.COBBLESTONE_WALL, 1.5, "wall", failures);
            surface(context, world, base, Blocks.STONE_SLAB, 0.5, "slab", failures);
            cappedBlocks(context, world, base, failures);
            jesusMeasuresShallowAndDeepWater(context, world, base);
            noFallMeasuresActualDamage(context, world, base);
        } finally {
            context.getInput().releaseKey(options -> options.keyUp);
            context.runOnClient(client -> {
                AgalarHackClient.moduleManager.getModule("Parkour").setToggled(false, false);
                AgalarHackClient.moduleManager.getModule("Jesus").setToggled(false, false);
                AgalarHackClient.moduleManager.getModule("NoFall").setToggled(false, false);
                var module = AgalarHackClient.moduleManager.getModule("BlockESP");
                module.setToggled(false, false);
                module.settings.setSetting("horizontalRange", 24.0);
                module.settings.setSetting("verticalRange", 16.0);
                module.settings.setSetting("maxResults", 1024.0);
            });
        }
        if (!failures.isEmpty()) throw new AssertionError("Remaining safety regressions: " + failures);
    }

    /** Uses real source-fluid states at two depths and checks buoyancy points at their actual tops. */
    private static void jesusMeasuresShallowAndDeepWater(ClientGameTestContext context,
            TestSingleplayerContext world, BlockPos base) {
        BlockPos shallow = base.offset(2, 0, 8);
        BlockPos deep = base.offset(8, 0, 8);
        world.getServer().runOnServer(server -> {
            var level = world.getConnection().getServerPlayer().level();
            for (int depth = 0; depth < 2; depth++)
                level.setBlockAndUpdate(shallow.above(depth), Blocks.WATER.defaultBlockState());
            for (int depth = 0; depth < 8; depth++)
                level.setBlockAndUpdate(deep.above(depth), Blocks.WATER.defaultBlockState());
        });
        world.getConnection().waitForChunksRender();

        double shallowSurface = context.computeOnClient(client -> Jesus.waterSurfaceY(client.level, shallow));
        double deepSurface = context.computeOnClient(client -> Jesus.waterSurfaceY(client.level, deep));
        if (Math.abs(shallowSurface - (shallow.getY() + 2.0)) > 0.001
                || Math.abs(deepSurface - (deep.getY() + 8.0)) > 0.001) {
            throw new AssertionError("Jesus did not derive both water surfaces from live fluid states: shallow="
                    + shallowSurface + " deep=" + deepSurface);
        }

        var module = (me.mrhakan.agalarhack.module.movement.Jesus)
                AgalarHackClient.moduleManager.getModule("Jesus");
        module.setToggled(false, false);
        assertUpwardSurfaceCorrection(context, world, module, shallow);
        assertUpwardSurfaceCorrection(context, world, module, deep);
        LOGGER.info("  Jesus used measured shallow={} and deep={} water surfaces", shallowSurface, deepSurface);
    }

    private static void assertUpwardSurfaceCorrection(ClientGameTestContext context,
            TestSingleplayerContext world, Jesus module, BlockPos water) {
        module.setToggled(false, false);
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(water.getX() + 0.5, water.getY() + 0.1, water.getZ() + 0.5);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
        });
        context.waitTicks(5);
        double correction = context.computeOnClient(client -> {
            module.setToggled(true, false);
            client.player.setDeltaMovement(Vec3.ZERO);
            module.onUpdate();
            double y = client.player.getDeltaMovement().y;
            module.setToggled(false, false);
            return y;
        });
        if (!(correction > 0)) {
            throw new AssertionError("Jesus did not raise a submerged player toward the surface at "
                    + water + "; vertical correction=" + correction);
        }
    }

    /** Records server-side health loss for an unmodified fall and for NoFall's one-shot report. */
    private static void noFallMeasuresActualDamage(ClientGameTestContext context,
            TestSingleplayerContext world, BlockPos base) {
        BlockPos floor = base.offset(26, 0, 12);
        world.getServer().runOnServer(server -> {
            var level = world.getConnection().getServerPlayer().level();
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                level.setBlockAndUpdate(floor.offset(x, 0, z), Blocks.STONE.defaultBlockState());
        });
        float controlDamage = measuredFallDamage(context, world, floor, false);
        float noFallDamage = measuredFallDamage(context, world, floor, true);
        LOGGER.info("  NoFall measured server health loss: control={} enabled={}", controlDamage, noFallDamage);
        if (controlDamage <= 0) {
            throw new AssertionError("NoFall damage control was not calibrated: an ordinary fall dealt "
                    + controlDamage + " health damage");
        }
        if (noFallDamage >= controlDamage) {
            throw new AssertionError("NoFall did not reduce real server damage against the calibrated control: "
                    + noFallDamage + " >= " + controlDamage);
        }
        AgalarHackClient.moduleManager.getModule("NoFall").setToggled(false, false);
    }

    private static float measuredFallDamage(ClientGameTestContext context, TestSingleplayerContext world,
            BlockPos floor, boolean enabled) {
        context.runOnClient(client -> AgalarHackClient.moduleManager.getModule("NoFall").setToggled(false, false));
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.setGameMode(GameType.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
            player.teleportTo(floor.getX() + 0.5, floor.getY() + 12, floor.getZ() + 0.5);
        });
        context.waitTicks(5);
        context.runOnClient(client -> AgalarHackClient.moduleManager.getModule("NoFall").setToggled(enabled, false));
        boolean landed = false;
        for (int tick = 0; tick < 160; tick += 4) {
            context.waitTicks(4);
            if (context.computeOnClient(client -> client.player.onGround())) {
                landed = true;
                break;
            }
        }
        if (!landed) throw new AssertionError("NoFall damage measurement did not land within 160 ticks");
        context.waitTicks(5);
        float health = world.getServer().computeOnServer(server ->
                world.getConnection().getServerPlayer().getHealth());
        return Math.max(0, 20.0f - health);
    }

    private static void surface(ClientGameTestContext context, TestSingleplayerContext world,
            BlockPos base, Block block, double height, String label, List<String> failures) {
        world.getServer().runOnServer(server -> {
            var level = world.getConnection().getServerPlayer().level();
            for (int x = -2; x <= 20; x++) for (int z = -1; z <= 1; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                for (int y = 0; y <= 4; y++)
                    level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            for (int x = 0; x <= 18; x++) level.setBlockAndUpdate(base.offset(x, 0, 0), block.defaultBlockState());
        });
        world.getConnection().waitForChunksRender();
        double control = walkSurface(context, world, base, height, false, label);
        double enabled = walkSurface(context, world, base, height, true, label);
        check(control < 0.2, label + " control rose " + control + "; fixture was not a level walkway", failures);
        check(enabled < 0.2, "Parkour falsely jumped " + enabled + " on continuous " + label
                + " support (disabled control " + control + ")", failures);
        LOGGER.info("  Parkour {} surface: control rise={} enabled rise={}", label, control, enabled);
    }

    private static double walkSurface(ClientGameTestContext context, TestSingleplayerContext world,
            BlockPos base, double height, boolean enabled, String label) {
        context.runOnClient(client -> AgalarHackClient.moduleManager.getModule("Parkour").setToggled(false, false));
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(base.getX() + 2.5, base.getY() + height, base.getZ() + 0.5);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
            player.setHealth(player.getMaxHealth());
        });
        context.waitTicks(30);
        context.waitFor(client -> client.player.onGround(), 100);
        context.getInput().lookAt(base.offset(18, 2, 0));
        context.waitTicks(5);
        double start = context.computeOnClient(client -> {
            var player = client.player;
            if (Math.abs(player.getY() - (base.getY() + height)) > 0.05)
                throw new AssertionError(label + " fixture did not place the player on the actual surface: " + player.getY());
            var box = player.getBoundingBox();
            var feet = new AABB(box.minX, box.minY - 0.2, box.minZ, box.maxX, box.minY, box.maxZ);
            // This exercises the actual 26.2 API before changing Parkour's production predicate.
            if (!client.level.getBlockCollisions(player, feet).iterator().hasNext())
                throw new AssertionError(label + " fixture has no vanilla collision support");
            AgalarHackClient.moduleManager.getModule("Parkour").setToggled(enabled, false);
            return player.getY();
        });
        double peak = start;
        double startX = context.computeOnClient(client -> client.player.getX());
        context.getInput().holdKey(options -> options.keyUp);
        try {
            for (int tick = 0; tick < 30; tick++) {
                context.waitTicks(1);
                peak = Math.max(peak, context.computeOnClient(client -> client.player.getY()));
            }
        } finally {
            context.getInput().releaseKey(options -> options.keyUp);
            context.runOnClient(client -> AgalarHackClient.moduleManager.getModule("Parkour").setToggled(false, false));
        }
        double travelled = context.computeOnClient(client -> client.player.getX()) - startX;
        if (travelled < 2 || travelled > 12) throw new AssertionError(label + " walk did not stay inside the level fixture: " + travelled);
        context.waitTicks(10);
        return peak - start;
    }

    private static void cappedBlocks(ClientGameTestContext context, TestSingleplayerContext world,
            BlockPos base, List<String> failures) {
        List<BlockPos> near = new ArrayList<>(), later = new ArrayList<>();
        for (int x = 4; x <= 8; x++) near.add(base.offset(x, -1, -4));
        for (int x = 16; x <= 20; x++) for (int z = -4; z <= 0; z++) later.add(base.offset(x, -1, z));
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            for (int x = 0; x <= 18; x++) player.level().setBlockAndUpdate(base.offset(x, 0, 0), Blocks.AIR.defaultBlockState());
            near.forEach(pos -> player.level().setBlockAndUpdate(pos, Blocks.DIAMOND_ORE.defaultBlockState()));
            later.forEach(pos -> player.level().setBlockAndUpdate(pos, Blocks.DIAMOND_ORE.defaultBlockState()));
            player.teleportTo(base.getX() + 8.5, base.getY(), base.getZ() + 0.5);
            player.setDeltaMovement(Vec3.ZERO);
        });
        context.waitTicks(30);
        context.waitFor(client -> later.stream().allMatch(pos -> client.level.getBlockState(pos).is(Blocks.DIAMOND_ORE)), 100);
        context.runOnClient(client -> {
            var module = (BlockESP) AgalarHackClient.moduleManager.getModule("BlockESP");
            module.settings.setSetting("horizontalRange", 12.0);
            module.settings.setSetting("verticalRange", 4.0);
            module.settings.setSetting("maxResults", 16.0);
            module.setToggled(true, false);
        });
        context.waitTicks(100); // bounded 25x25x9 scan, using the real shared tick budget
        int initial = context.computeOnClient(client -> blockEsp().getMatches().size());
        check(initial == 16, "BlockESP did not reach its cap: " + initial, failures);
        int maximumWork = 0;
        for (int tick = 0; tick < 10; tick++) {
            context.waitTicks(1);
            maximumWork = Math.max(maximumWork, context.computeOnClient(client ->
                    ClientServices.require(ScannerService.class).lastUsage().blocks()));
        }
        check(maximumWork <= 9, "Static capped BlockESP kept probing blocks: " + maximumWork
                + " charged positions in one tick, only nine chunk skips needed", failures);
        context.runOnClient(client -> blockEsp().settings.setSetting("maxResults", 32.0));
        context.waitTicks(100);
        int grown = context.computeOnClient(client -> blockEsp().getMatches().size());
        check(grown == 30, "Increasing the cap stranded earlier markers: " + grown + "/30", failures);
        context.runOnClient(client -> blockEsp().settings.setSetting("maxResults", 16.0));
        context.waitTicks(30);
        world.getServer().runOnServer(server -> later.forEach(pos ->
                world.getConnection().getServerPlayer().level().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState())));
        context.waitFor(client -> later.stream().noneMatch(pos -> client.level.getBlockState(pos).is(Blocks.DIAMOND_ORE)), 100);
        context.waitTicks(100);
        var remaining = context.computeOnClient(client -> blockEsp().getMatches());
        check(remaining.size() == near.size() && remaining.containsAll(near),
                "After removal the earlier chunk did not recover: " + remaining, failures);
        context.runOnClient(client -> blockEsp().setToggled(false, false));
        LOGGER.info("  BlockESP cap recovery: initial={} grown={} remaining={} idle maximum work={}",
                initial, grown, remaining.size(), maximumWork);
    }

    private static BlockESP blockEsp() {
        return (BlockESP) AgalarHackClient.moduleManager.getModule("BlockESP");
    }

    private static void check(boolean condition, String message, List<String> failures) {
        if (!condition) failures.add(message);
    }
}
