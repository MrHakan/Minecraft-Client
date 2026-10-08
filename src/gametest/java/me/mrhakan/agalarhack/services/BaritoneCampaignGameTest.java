package me.mrhakan.agalarhack.services;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Opt-in real Baritone 26.2 acceptance; never loaded by the absence/control suite. */
public final class BaritoneCampaignGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        BaritoneBridge bridge = new BaritoneBridge();
        if (!bridge.available()) throw new AssertionError("Installed-Baritone acceptance requires the real compatible jar");
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(true).create()) {
            context.runOnClient(c -> c.options.renderDistance().set(4));
            world.getServer().runOnServer(s -> s.getPlayerList().setViewDistance(4));
            world.getConnection().waitForChunksRender();
            BlockPos base = world.getServer().computeOnServer(s -> world.getConnection().getServerPlayer().blockPosition());
            BlockPos remote = base.offset(384, 0, 0), chest = base.offset(2, 0, 0);
            world.getServer().runOnServer(s -> {
                var player = world.getConnection().getServerPlayer(); var level = player.level();
                player.setGameMode(GameType.SURVIVAL); player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(20);
                for (int x = -3; x <= 392; x++) for (int z = -4; z <= 4; z++) {
                    level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    for (int y = 0; y <= 10; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                // A room forces the return path through a real wooden door to the chest.
                for (int x = 0; x <= 6; x++) for (int z = -2; z <= 2; z++) {
                    if (x == 0 || x == 6 || z == -2 || z == 2) for (int y = 0; y <= 2; y++)
                        level.setBlockAndUpdate(base.offset(x, y, z), Blocks.OAK_PLANKS.defaultBlockState());
                    level.setBlockAndUpdate(base.offset(x, 3, z), Blocks.OAK_PLANKS.defaultBlockState());
                }
                var door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
                level.setBlockAndUpdate(base.offset(6, 0, 0), door);
                level.setBlockAndUpdate(base.offset(6, 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
                for (int x = 2; x <= 6; x++) {
                    level.setBlockAndUpdate(remote.offset(x, 0, 1), Blocks.IRON_ORE.defaultBlockState());
                    level.setBlockAndUpdate(remote.offset(x, 0, -1), Blocks.DIAMOND_ORE.defaultBlockState());
                }
                player.teleportTo(base.getX() + 8.5, base.getY(), base.getZ() + 0.5);
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
                player.getInventory().setItem(1, new ItemStack(Items.COOKED_BEEF, 24));
                player.getInventory().setItem(9, new ItemStack(Items.OAK_PLANKS, 64));
                player.getInventory().setSelectedSlot(0); player.inventoryMenu.broadcastChanges();
            });
            context.waitTicks(30);
            GrindExecutor g = context.computeOnClient(c -> ClientServices.require(GrindExecutor.class));
            SurvivalTasks upkeep = new SurvivalTasks(g, SurvivalProgression.Tier.IRON, base);
            context.runOnClient(c -> g.useBaritone(true));
            travel(context, g, upkeep, remote, 4000);
            context.waitTicks(100);
            if (context.computeOnClient(c -> c.level.getChunkSource().getChunkNow(chest.getX() >> 4, chest.getZ() >> 4) != null))
                throw new AssertionError("Return fixture did not unload the base chunk");
            context.runOnClient(c -> {
                if (g.start("raw_iron", 1) != GrindExecutor.StartResult.STARTED) throw new AssertionError("Remote mining did not start");
            });
            for (int i = 0; i < 600 && context.computeOnClient(c -> g.running()); i++) context.waitTicks(1);
            if (context.computeOnClient(c -> g.state()) != TaskRunner.State.DONE || context.computeOnClient(c -> g.count("raw_iron")) < 1)
                throw new AssertionError("Real Baritone remote mining failed: " + context.computeOnClient(c -> g.blockedReason()) + " / " + context.computeOnClient(c -> g.failure()));
            // Lose harvesting capability mid-goal, then rebuild through shared station/click services.
            world.getServer().runOnServer(s -> {
                var player = world.getConnection().getServerPlayer();
                ItemStack worn = new ItemStack(Items.IRON_PICKAXE); worn.setDamageValue(worn.getMaxDamage() - 2);
                player.getInventory().setItem(0, worn);
                player.getInventory().setItem(2, new ItemStack(Items.STONE_PICKAXE));
                player.getInventory().setItem(3, new ItemStack(Items.IRON_INGOT, 3));
                player.getInventory().setItem(4, new ItemStack(Items.STICK, 2));
                player.getInventory().setItem(5, new ItemStack(Items.CRAFTING_TABLE));
                player.inventoryMenu.broadcastChanges();
            });
            context.waitTicks(20);
            TaskRunner repair = new TaskRunner();
            context.runOnClient(c -> repair.start(List.of(new SurvivalTasks.ItemGoalTask(g, "diamond", 4, false))));
            for (int i = 0; i < 1200 && repair.running(); i++) {
                context.runOnClient(c -> { if (!upkeep.maintain()) repair.tick(); }); context.waitTicks(1);
            }
            if (repair.state() != TaskRunner.State.DONE || context.computeOnClient(c -> g.count("iron_pickaxe")) < 1)
                throw new AssertionError("Installed-Baritone tool replacement failed: " + repair.state() + " / " + repair.currentTask()
                        + " / " + repair.blockedReason() + " / " + repair.failure() + " diamonds=" + context.computeOnClient(c -> g.count("diamond")));
            TaskRunner storage = new TaskRunner();
            context.runOnClient(c -> storage.start(List.of(new SurvivalTasks.StorageTask(g, List.of(chest)))));
            for (int i = 0; i < 5000 && storage.running(); i++) {
                context.runOnClient(c -> { if (!upkeep.maintain()) storage.tick(); }); context.waitTicks(1);
            }
            if (storage.state() != TaskRunner.State.DONE) throw new AssertionError("Real Baritone return/storage failed: " + storage.blockedReason() + " / " + storage.failure());
            int deposited = world.getServer().computeOnServer(s -> {
                var entity = (net.minecraft.world.level.block.entity.ChestBlockEntity) world.getConnection().getServerPlayer().level().getBlockEntity(chest);
                int total = 0; for (int i = 0; i < 27; i++) if (entity.getItem(i).is(Items.OAK_PLANKS)) total += entity.getItem(i).getCount();
                return total;
            });
            if (deposited != 32 || context.computeOnClient(c -> g.count("planks")) != 32
                    || !context.computeOnClient(c -> c.player.containerMenu.getCarried().isEmpty()))
                throw new AssertionError("Return journey did not preserve plank/cursor accounting");
            boolean roomIntact = world.getServer().computeOnServer(s -> {
                var level = world.getConnection().getServerPlayer().level();
                for (int x = 0; x <= 6; x++) for (int z = -2; z <= 2; z++) {
                    if (!level.getBlockState(base.offset(x, 3, z)).is(Blocks.OAK_PLANKS)) return false;
                    if (x == 0 || x == 6 || z == -2 || z == 2) for (int y = 0; y <= 2; y++) {
                        if (x == 6 && z == 0 && y < 2) continue;
                        if (!level.getBlockState(base.offset(x, y, z)).is(Blocks.OAK_PLANKS)) return false;
                    }
                }
                return true;
            });
            if (!roomIntact) throw new AssertionError("Return access damaged the room instead of using its door");
            context.waitTicks(20);
            context.runOnClient(c -> {
                g.stop();
                BlockPos adjacent = c.player.blockPosition().offset(1, 0, 0);
                if (!g.travel.moveTo(adjacent) || !bridge.goalActive()) throw new AssertionError("Adjacent exact goal did not start");
                if (!g.travel.moveTo(adjacent) || !bridge.goalActive())
                    throw new AssertionError("AutoGrind completed an exact goal before entering its block");
                g.travel.cancelMovement();
            });
            context.waitTicks(20);
            context.runOnClient(c -> {
                g.stop();
                if (!g.travel.moveTo(remote)) throw new AssertionError("Ownership fixture did not start");
                if (bridge.pathTo(base.getX() + 8, base.getY(), base.getZ()) != BaritoneBridge.Result.STARTED)
                    throw new AssertionError("Replacement goal did not start");
                g.travel.cancelMovement();
                if (!bridge.ownsGoal() || !bridge.goalActive()) throw new AssertionError("AutoGrind cancelled a replacement goal");
                bridge.cancelGoal(); g.stop();
            });
            org.slf4j.LoggerFactory.getLogger("agalarhack-gametest").info("Real Baritone 26.2: 384-block travel, remote mining, mid-goal tool rebuilding, unloaded-base return, door/chest access and replacement-goal ownership passed");
        } finally {
            closeFixtureWorkers(context);
        }
    }
    /**
     * Upstream 1.19.0 keeps non-daemon cache workers alive after the world closes. Minecraft 26.2
     * then trips its shutdown watchdog. Close only this fixture's pinned Baritone executor after
     * saving/closing the world; no executor reflection or shutdown code ships in the client jar.
     */
    private static void closeFixtureWorkers(ClientGameTestContext context) {
        try {
            Object primary = context.computeOnClient(c -> {
                Object provider = Class.forName("baritone.api.BaritoneAPI").getMethod("getProvider").invoke(null);
                return Class.forName("baritone.api.IBaritoneProvider").getMethod("getPrimaryBaritone").invoke(provider);
            });
            for (var field : primary.getClass().getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        || !java.util.concurrent.ExecutorService.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                var executor = (java.util.concurrent.ExecutorService) field.get(null);
                executor.shutdownNow();
                if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
                    throw new AssertionError("Pinned Baritone fixture workers did not terminate");
                return;
            }
            throw new AssertionError("Pinned Baritone fixture executor was not found");
        } catch (ReflectiveOperationException | InterruptedException failure) {
            throw new AssertionError("Could not close pinned Baritone fixture workers", failure);
        }
    }
    private static void travel(ClientGameTestContext context, GrindExecutor g, SurvivalTasks upkeep, BlockPos target, int budget) {
        for (int i = 0; i < budget; i++) {
            if (context.computeOnClient(c -> c.player.blockPosition().closerThan(target, 1.5))) {
                context.runOnClient(c -> g.travel.cancelMovement()); return;
            }
            context.runOnClient(c -> {
                if (upkeep.maintain()) return;
                if (!g.travel.moveTo(target)) throw new AssertionError(g.travel.movementProblem("Real Baritone travel could not start"));
            });
            context.waitTicks(1);
        }
        throw new AssertionError("Real Baritone travel exceeded its budget");
    }
}
