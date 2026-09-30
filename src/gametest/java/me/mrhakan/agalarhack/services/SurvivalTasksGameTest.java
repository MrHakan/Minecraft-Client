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
            if (stored != 32 || context.computeOnClient(c -> g.count("planks")) != 32)
                throw new AssertionError("Storage did not preserve 32 carried planks; deposited=" + stored);
            run(context, new SurvivalTasks.EquipTask(g), 100);
            if (!context.computeOnClient(c -> c.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
                    && c.player.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD))) throw new AssertionError("Survival equipment was not worn");
            context.runOnClient(c -> g.stop());
            inventory(world, p -> {
                p.getInventory().setItem(0, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
                p.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 7));
                p.getInventory().setItem(2, new ItemStack(Items.NETHERRACK));
                p.getInventory().setItem(3, new ItemStack(Items.CRAFTING_TABLE));
            });
            context.waitTicks(20);
            run(context, new SurvivalTasks.TemplateTask(g, 2), 500);
            if (context.computeOnClient(c -> g.count("netherite_upgrade_smithing_template")) != 2
                    || context.computeOnClient(c -> g.count("diamond")) != 0) throw new AssertionError("Template duplication did not consume seven diamonds and yield two templates");
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
            org.slf4j.LoggerFactory.getLogger("agalarhack-gametest").info("Survival placement, storage, equipment, template duplication and enchanting passed");
        }
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
