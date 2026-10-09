package me.mrhakan.agalarhack.services;

import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Exercises survival placements, menu mappings, storage reserves, and equipment in a real world. */
public final class SurvivalTasksGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(true).create()) {
            world.getConnection().waitForChunksRender();
            BlockPos base = world.getServer().computeOnServer(server -> world.getConnection().getServerPlayer().blockPosition());
            world.getServer().runOnServer(server -> {
                ServerPlayer p = world.getConnection().getServerPlayer();
                p.setGameMode(GameType.SURVIVAL); p.setHealth(p.getMaxHealth()); p.getFoodData().setFoodLevel(20);
                for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
                    p.level().setBlockAndUpdate(base.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    for (int y = 0; y <= 6; y++) p.level().setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                p.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
            });
            context.waitTicks(20);
            inventory(world, p -> {
                p.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 8));
                p.getInventory().setItem(1, new ItemStack(Items.CHEST));
                p.getInventory().setItem(9, new ItemStack(Items.OAK_PLANKS, 64));
                p.getInventory().setItem(10, new ItemStack(Items.IRON_CHESTPLATE));
                p.getInventory().setItem(11, new ItemStack(Items.SHIELD));
            });
            context.waitTicks(20);
            GrindExecutor g = context.computeOnClient(client -> ClientServices.require(GrindExecutor.class));
            context.runOnClient(client -> g.useBaritone(false));
            BlockPos foundation = base.offset(2, 0, 0), chest = foundation.above();
            run(context, new SurvivalTasks.PlaceTask(g, foundation, "cobblestone"), 160);
            run(context, new SurvivalTasks.PlaceTask(g, chest, "chest"), 160);
            if (!context.computeOnClient(c -> c.level.getBlockState(chest).is(Blocks.CHEST))) throw new AssertionError("Chest placement was not observed");
            run(context, new SurvivalTasks.StorageTask(g, List.of(chest)), 200);
            int stored = world.getServer().computeOnServer(server -> {
                var entity = (net.minecraft.world.level.block.entity.ChestBlockEntity) world.getConnection().getServerPlayer().level().getBlockEntity(chest);
                int total = 0; for (int i = 0; i < 27; i++) if (entity.getItem(i).is(Items.OAK_PLANKS)) total += entity.getItem(i).getCount();
                return total;
            });
            if (stored != 32 || context.computeOnClient(c -> g.items.count("planks")) != 32)
                throw new AssertionError("Storage did not preserve 32 carried planks; deposited=" + stored);
            run(context, new SurvivalTasks.EquipTask(g), 100);
            if (!context.computeOnClient(c -> c.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
                    && c.player.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD))) throw new AssertionError("Survival equipment was not worn");
            context.runOnClient(c -> g.stop());
            // A full chest still has capacity inside an existing stack.
            world.getServer().runOnServer(server -> {
                var entity = (net.minecraft.world.level.block.entity.ChestBlockEntity) world.getConnection().getServerPlayer().level().getBlockEntity(chest);
                for (int i = 1; i < 27; i++) entity.setItem(i, new ItemStack(Items.DIRT, 64));
            });
            inventory(world, p -> {
                p.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
                p.getInventory().setItem(9, new ItemStack(Items.OAK_PLANKS, 64));
            });
            context.waitTicks(20);
            run(context, new SurvivalTasks.StorageTask(g, List.of(chest)), 240);
            if (context.computeOnClient(c -> g.items.count("planks")) != 32)
                throw new AssertionError("Storage did not merge planks into the full chest");
            world.getServer().runOnServer(server -> {
                var entity = (net.minecraft.world.level.block.entity.ChestBlockEntity) world.getConnection().getServerPlayer().level().getBlockEntity(chest);
                entity.setItem(1, ItemStack.EMPTY);
            });
            inventory(world, p -> {
                p.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
                p.getInventory().setItem(1, new ItemStack(Items.WOODEN_PICKAXE));
            });
            context.waitTicks(20);
            run(context, new SurvivalTasks.StorageTask(g, List.of(chest)), 160);
            if (context.computeOnClient(c -> g.items.count("wooden_pickaxe")) != 0 || context.computeOnClient(c -> g.items.count("iron_pickaxe")) != 1)
                throw new AssertionError("Storage did not retain the iron tool and deposit obsolete gear");
            context.runOnClient(c -> g.stop());
            // Inventory pressure must keep a large active goal and return every carried stack.
            world.getServer().runOnServer(server -> {
                var entity = (net.minecraft.world.level.block.entity.ChestBlockEntity) world.getConnection().getServerPlayer().level().getBlockEntity(chest);
                entity.clearContent();
            });
            inventory(world, p -> {
                for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.DIRT));
                p.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 49));
                p.getInventory().setItem(1, new ItemStack(Items.IRON_PICKAXE));
            });
            context.waitTicks(20);
            context.runOnClient(c -> {
                g.startSurvival(SurvivalProgression.Tier.MAX);
                g.registerStorage(List.of(chest));
                g.stop();
                // Restarting in the same loaded world must retain completed base storage.
                g.startSurvival(SurvivalProgression.Tier.MAX);
                // Should no plot fit the MAX buildings around this arena, the campaign starts paused;
                // resuming accepts the default corner, which is all this scenario needs.
                g.resume();
                g.useBaritone(false);
            });
            for (int i = 0; i < 300; i++) {
                context.waitTicks(1);
                if (i > 0 && context.computeOnClient(c -> c.player.getInventory().getFreeSlot() >= 0
                        && (g.currentTask() == null || !g.currentTask().startsWith("inventory recovery:")))) break;
            }
            if (!context.computeOnClient(c -> g.items.count("diamond") == 49
                    && c.player.getInventory().getFreeSlot() >= 0 && c.player.containerMenu.getCarried().isEmpty()))
                throw new AssertionError("Pressure storage lost the active diamond goal or left a cursor stack");
            context.runOnClient(c -> g.stop());
            // A pickaxe with only one use remaining must trigger a replacement prerequisite.
            inventory(world, p -> {
                ItemStack worn = new ItemStack(Items.IRON_PICKAXE); worn.setDamageValue(worn.getMaxDamage() - 1);
                p.getInventory().setItem(0, worn);
                p.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS, 3));
                p.getInventory().setItem(2, new ItemStack(Items.STICK, 2));
                p.getInventory().setItem(3, new ItemStack(Items.CRAFTING_TABLE));
            });
            context.waitTicks(20);
            if (context.computeOnClient(c -> SurvivalTasks.gearSatisfied(g, "iron_pickaxe", 1)))
                throw new AssertionError("Near-broken pickaxe incorrectly satisfied a gear milestone");
            run(context, new SurvivalTasks.ItemGoalTask(g, "cobblestone", 1, false), 600);
            if (context.computeOnClient(c -> g.items.count("cobblestone")) < 1 || context.computeOnClient(c -> g.items.count("wooden_pickaxe")) < 1)
                throw new AssertionError("Resource goal did not rebuild a usable pickaxe");
            context.runOnClient(c -> g.stop());
            inventory(world, p -> {
                p.getInventory().setItem(0, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
                p.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 7));
                p.getInventory().setItem(2, new ItemStack(Items.NETHERRACK));
                p.getInventory().setItem(3, new ItemStack(Items.CRAFTING_TABLE));
            });
            context.waitTicks(20);
            run(context, new SurvivalTasks.TemplateTask(g, 2), 500);
            if (context.computeOnClient(c -> g.items.count("netherite_upgrade_smithing_template")) != 2
                    || context.computeOnClient(c -> g.items.count("diamond")) != 0) throw new AssertionError("Template duplication did not consume seven diamonds and yield two templates");
            context.runOnClient(c -> g.stop());
            BlockPos table = base.offset(0, 0, 4);
            world.getServer().runOnServer(server -> {
                ServerPlayer p = world.getConnection().getServerPlayer();
                p.level().setBlockAndUpdate(table, Blocks.ENCHANTING_TABLE.defaultBlockState());
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                    if ((Math.abs(x) == 2 || Math.abs(z) == 2) && !(x == 0 && z == -2))
                        p.level().setBlockAndUpdate(table.offset(x, 0, z), Blocks.BOOKSHELF.defaultBlockState());
                }
                p.teleportTo(table.getX() + 0.5, table.getY(), table.getZ() - 0.5);
                p.setExperienceLevels(30);
            });
            inventory(world, p -> {
                p.getInventory().setItem(0, new ItemStack(Items.NETHERITE_SWORD));
                p.getInventory().setItem(1, new ItemStack(Items.LAPIS_LAZULI, 3));
            });
            context.waitTicks(20);
            run(context, new SurvivalTasks.EnchantTask(g, table), 400);
            if (!context.computeOnClient(c -> {
                int index = g.inventory.findInventory(stack -> stack.is(Items.NETHERITE_SWORD));
                var enchants = index < 0 ? null : g.inventory.stackAt(index).get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
                return enchants != null && !enchants.isEmpty() && c.player.experienceLevel == 27;
            })) throw new AssertionError("Level-30 enchanting did not return enchanted gear and consume three levels");
            context.runOnClient(c -> g.stop());
            storedSuppliesAreTakenBack(context, world, g, base, chest);
            buildPlotSearch(context, world, g, base.offset(0, 0, 96));
            org.slf4j.LoggerFactory.getLogger("agalarhack-gametest").info("Survival placement, storage, equipment, template duplication, enchanting, storage withdrawal and build plot search passed");
        }
    }
    /**
     * D2: a campaign goal takes back what AutoGrind itself stored instead of gathering it. The test
     * area is a stone floor with no ore, so without the withdrawal an iron pickaxe would pause for
     * manual movement. A renamed stack the player keeps in the same chest must stay where it is.
     */
    private static void storedSuppliesAreTakenBack(ClientGameTestContext context, TestSingleplayerContext world,
            GrindExecutor g, BlockPos base, BlockPos chest) {
        world.getServer().runOnServer(server -> {
            ServerPlayer p = world.getConnection().getServerPlayer();
            var entity = (net.minecraft.world.level.block.entity.ChestBlockEntity) p.level().getBlockEntity(chest);
            entity.clearContent();
            ItemStack keepsake = new ItemStack(Items.IRON_INGOT, 5);
            keepsake.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Keepsake"));
            entity.setItem(0, keepsake);
            p.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        });
        // Surplus over the storage policy's reserves (16 ingots, 8 sticks) goes into the chest.
        inventory(world, p -> {
            p.getInventory().setItem(0, new ItemStack(Items.CRAFTING_TABLE));
            p.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 19));
            p.getInventory().setItem(10, new ItemStack(Items.STICK, 12));
        });
        context.waitTicks(20);
        context.runOnClient(c -> {
            g.startSurvival(SurvivalProgression.Tier.IRON);
            g.registerStorage(List.of(chest));
            g.stop();
            g.useBaritone(false);
        });
        run(context, new SurvivalTasks.StorageTask(g, List.of(chest)), 240);
        int[] deposited = chestContents(world, chest);
        if (deposited[1] != 3 || deposited[2] != 4 || deposited[0] != 5)
            throw new AssertionError("Surplus storage expected 3 ingots, 4 sticks and the 5-ingot keepsake; found "
                    + deposited[1] + ", " + deposited[2] + ", " + deposited[0]);
        // The carried reserve is spent elsewhere; only the crafting table remains.
        inventory(world, p -> p.getInventory().setItem(0, new ItemStack(Items.CRAFTING_TABLE)));
        context.waitTicks(20);
        run(context, new SurvivalTasks.ItemGoalTask(g, "iron_pickaxe", 1, false), 1200);
        int[] left = chestContents(world, chest);
        if (context.computeOnClient(c -> g.items.count("iron_pickaxe")) != 1)
            throw new AssertionError("The iron pickaxe was not made from stored ingots and sticks");
        if (left[0] != 5) throw new AssertionError("The renamed keepsake ingots were taken: " + left[0] + " left");
        if (left[1] != 0 || left[2] != 2)
            throw new AssertionError("Withdrawal should take 3 ingots and 2 of 4 sticks; left " + left[1] + " ingots, " + left[2] + " sticks");
        if (!context.computeOnClient(c -> c.player.containerMenu.getCarried().isEmpty() && c.gui.screen() == null))
            throw new AssertionError("Withdrawal left a cursor stack or the chest open");
        context.runOnClient(c -> g.stop());
    }

    /**
     * D3: on rough ground the campaign finds a level plot nearby instead of building where it
     * stands, and says so plainly when there is none. The arena is level stone with a two-high
     * pillar on every third column, so no house footprint fits anywhere in reach, until a clean
     * patch is opened a few blocks east.
     */
    private static void buildPlotSearch(ClientGameTestContext context, TestSingleplayerContext world, GrindExecutor g, BlockPos start) {
        int reach = GrindPlot.SEARCH_RADIUS + 12;
        world.getServer().runOnServer(server -> {
            ServerPlayer p = world.getConnection().getServerPlayer();
            var level = p.level();
            for (int x = -reach; x <= reach; x++) for (int z = -reach; z <= reach; z++) {
                level.setBlockAndUpdate(start.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                boolean pillar = Math.floorMod(x, 3) == 1 && Math.floorMod(z, 3) == 1;
                for (int y = 0; y <= GrindPlot.SCAN_TOP + 1; y++)
                    level.setBlockAndUpdate(start.offset(x, y, z), pillar && y <= 1 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            p.teleportTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        });
        world.getConnection().waitForChunksRender();
        context.waitTicks(40);
        context.runOnClient(c -> {
            g.reset();
            if (g.startSurvival(SurvivalProgression.Tier.IRON) != GrindExecutor.StartResult.STARTED)
                throw new AssertionError("The campaign did not start on rough ground");
            if (g.state() != TaskRunner.State.NEEDS_MOVEMENT || g.blockedReason() == null || !g.blockedReason().startsWith("No clear, level spot"))
                throw new AssertionError("Rough ground with no plot should pause with a clear reason; state=" + g.state() + " reason=" + g.blockedReason());
            g.stop();
        });
        // A clean patch east of the start: x 8..16, z -3..5 from the player.
        world.getServer().runOnServer(server -> {
            var level = world.getConnection().getServerPlayer().level();
            for (int x = 8; x <= 16; x++) for (int z = -3; z <= 5; z++) for (int y = 0; y <= 1; y++)
                level.setBlockAndUpdate(start.offset(x, y, z), Blocks.AIR.defaultBlockState());
        });
        context.waitTicks(20);
        int[] corner = context.computeOnClient(c -> {
            if (g.startSurvival(SurvivalProgression.Tier.IRON) != GrindExecutor.StartResult.STARTED || g.state() != TaskRunner.State.RUNNING)
                throw new AssertionError("The campaign did not start on the open patch; state=" + g.state() + " reason=" + g.blockedReason());
            String[] parts = g.baseCoordinates().split(" ");
            g.stop();
            return new int[]{Integer.parseInt(parts[0]) - start.getX(), Integer.parseInt(parts[1]) - start.getY(), Integer.parseInt(parts[2]) - start.getZ()};
        });
        // The iron house is five by five with a path cell north of it, so its corner must sit at x 8..12, z -2..1.
        if (corner[0] < 8 || corner[0] > 12 || corner[2] < -2 || corner[2] > 1 || corner[1] != 0)
            throw new AssertionError("The chosen plot " + corner[0] + " " + corner[1] + " " + corner[2] + " is not on the open patch");
        context.runOnClient(c -> g.reset());
    }

    /** Named iron ingots, unnamed iron ingots and sticks in the chest, read on the server. */
    private static int[] chestContents(TestSingleplayerContext world, BlockPos chest) {
        return world.getServer().computeOnServer(server -> {
            var entity = (net.minecraft.world.level.block.entity.ChestBlockEntity) world.getConnection().getServerPlayer().level().getBlockEntity(chest);
            int[] counts = new int[3];
            for (int i = 0; i < 27; i++) {
                ItemStack stack = entity.getItem(i);
                if (stack.is(Items.IRON_INGOT)) counts[stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) ? 0 : 1] += stack.getCount();
                else if (stack.is(Items.STICK)) counts[2] += stack.getCount();
            }
            return counts;
        });
    }

    private static void inventory(TestSingleplayerContext world, Consumer<ServerPlayer> fill) {
        world.getServer().runOnServer(server -> {
            ServerPlayer p = world.getConnection().getServerPlayer(); p.getInventory().clearContent(); fill.accept(p);
            p.getInventory().setSelectedSlot(0); p.inventoryMenu.broadcastChanges(); p.containerMenu.broadcastChanges();
        });
    }
    private static void run(ClientGameTestContext context, TaskRunner.Task task, int budget) {
        TaskRunner runner = new TaskRunner(); context.runOnClient(c -> runner.start(List.of(task)));
        for (int i = 0; i < budget; i++) {
            context.runOnClient(c -> runner.tick()); context.waitTicks(1);
            if (runner.state() != TaskRunner.State.RUNNING) break;
        }
        if (runner.state() != TaskRunner.State.DONE) throw new AssertionError(task.name() + ": " + runner.state()
                + " blocked=" + runner.blockedReason() + " failure=" + runner.failure());
    }
}
