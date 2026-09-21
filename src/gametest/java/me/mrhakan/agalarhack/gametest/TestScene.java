package me.mrhakan.agalarhack.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Puts something worth looking at around the player.
 *
 * <p>A flat test world is empty, and an empty world is a weak test: a scanner that throws the moment
 * it finds a chest passes happily in a world with no chests, and the whole "it ran without throwing"
 * claim collapses to "its inner loop never executed". So the modules get a chest, a shulker box, an
 * ore, a hole, a mob, a dropped item and a filled inventory to react to.
 *
 * <p>Built through the game's own types rather than chat commands. Commands fail silently here -
 * {@code runCommand} logs the parse error and returns - which is how the first draft of this test
 * ran for a full minute against a world where none of its setup had applied.
 */
final class TestScene {
    private TestScene() { }

    /** Everything is placed within a few blocks so it stays inside every module's default range. */
    static void build(ServerPlayer player) {
        player.setGameMode(GameType.CREATIVE);

        ServerLevel level = player.level();
        BlockPos base = player.blockPosition();

        // Storage, for StorageESP and the container-aware inventory modules.
        level.setBlockAndUpdate(base.offset(3, 0, 0), Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(base.offset(4, 0, 0), Blocks.TRAPPED_CHEST.defaultBlockState());
        level.setBlockAndUpdate(base.offset(5, 0, 0), Blocks.ENDER_CHEST.defaultBlockState());
        level.setBlockAndUpdate(base.offset(3, 0, 1), Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(base.offset(4, 0, 1), Blocks.SHULKER_BOX.defaultBlockState());

        // An ore for BlockESP and a two-deep obsidian pocket for HoleESP.
        level.setBlockAndUpdate(base.offset(-3, 0, 0), Blocks.DIAMOND_ORE.defaultBlockState());
        for (int[] side : new int[][]{{-5, 0}, {-7, 0}, {-6, -1}, {-6, 1}}) {
            level.setBlockAndUpdate(base.offset(side[0], 0, side[1]), Blocks.OBSIDIAN.defaultBlockState());
            level.setBlockAndUpdate(base.offset(side[0], 1, side[1]), Blocks.OBSIDIAN.defaultBlockState());
        }
        level.setBlockAndUpdate(base.offset(-6, 0, 0), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(base.offset(-6, 1, 0), Blocks.AIR.defaultBlockState());

        // A mob for the target selector, the aura and every entity overlay. Mob spawning is off in a
        // consistent-settings world, so nothing else will ever appear on its own.
        EntityTypes.ZOMBIE.spawn(level, base.offset(6, 0, 0), EntitySpawnReason.COMMAND);
        EntityTypes.ITEM.spawn(level, base.offset(2, 0, 0), EntitySpawnReason.COMMAND);

        // Things the player modules act on: a totem to swap in, armour to equip, food to eat,
        // a weapon and tools to select, a rod to fish with and blocks to refill from.
        for (ItemStack stack : new ItemStack[]{
                new ItemStack(Items.TOTEM_OF_UNDYING, 2),
                new ItemStack(Items.DIAMOND_HELMET),
                new ItemStack(Items.DIAMOND_CHESTPLATE),
                new ItemStack(Items.DIAMOND_LEGGINGS),
                new ItemStack(Items.DIAMOND_BOOTS),
                new ItemStack(Items.BREAD, 16),
                new ItemStack(Items.GOLDEN_APPLE, 4),
                new ItemStack(Items.DIAMOND_SWORD),
                new ItemStack(Items.DIAMOND_PICKAXE),
                new ItemStack(Items.DIAMOND_AXE),
                new ItemStack(Items.DIAMOND_SHOVEL),
                new ItemStack(Items.BOW),
                new ItemStack(Items.ARROW, 64),
                new ItemStack(Items.FISHING_ROD),
                new ItemStack(Items.OBSIDIAN, 64),
                new ItemStack(Items.ENDER_PEARL, 16),
                new ItemStack(Items.SPLASH_POTION),
                new ItemStack(Items.COBBLESTONE, 64)}) {
            player.addItem(stack);
        }
    }
}
