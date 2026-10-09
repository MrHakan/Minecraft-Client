package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The campaign's base storage: which chests it built, what AutoGrind last saw in each, and whether a
 * goal should visit them before it gathers.
 *
 * <p>A client cannot see into a chest it has not opened, so contents are what AutoGrind recorded the
 * last time it had that chest open, which covers everything it deposited itself. A registered chest
 * it has not opened this session is visited once to find out. Items a player adds by hand are found
 * the next time AutoGrind opens that chest.
 */
final class GrindStorage {
    /** Beyond this a trip back to base costs more than gathering where the player is. */
    static final double WITHDRAW_RANGE = 64;
    /** Taking a stack into an empty slot must leave this many free, the inventory-pressure line. */
    static final int KEEP_FREE_SLOTS = 2;

    private final GrindExecutor g;
    private List<BlockPos> chests = List.of();
    private final Map<BlockPos, List<GrindWithdrawal.Stored>> seen = new HashMap<>();

    GrindStorage(GrindExecutor g) {
        this.g = g;
    }

    List<BlockPos> chests() {
        return chests;
    }

    void register(List<BlockPos> chests) {
        this.chests = List.copyOf(chests);
    }

    /** A new world or a reset forgets the base and everything seen in it. */
    void clear() {
        chests = List.of();
        seen.clear();
    }

    /**
     * Personal items stay where the player put them: a renamed or enchanted stack, and a tool one use
     * from breaking, are never taken.
     */
    static boolean withdrawable(ItemStack stack) {
        if (!GrindItems.usable(stack) || stack.has(DataComponents.CUSTOM_NAME)) return false;
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        return enchantments == null || enchantments.isEmpty();
    }

    /** Records what an open storage chest holds; called each tick AutoGrind has one open. */
    void observe(BlockPos chest, ChestMenu menu) {
        int slots = Math.min(menu.getRowCount() * 9, menu.slots.size());
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (!withdrawable(stack)) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            String key = GrindBook.generic(id) + "|" + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            counts.computeIfAbsent(key, ignored -> new int[1])[0] += stack.getCount();
        }
        List<GrindWithdrawal.Stored> stored = new ArrayList<>(counts.size());
        counts.forEach((key, count) -> {
            int split = key.indexOf('|');
            stored.add(new GrindWithdrawal.Stored(key.substring(0, split), key.substring(split + 1), count[0]));
        });
        seen.put(chest.immutable(), List.copyOf(stored));
    }

    /** What AutoGrind last saw in a chest, or {@code null} if it has not opened it this session. */
    List<GrindWithdrawal.Stored> seen(BlockPos chest) {
        return seen.get(chest);
    }

    /**
     * The chests worth visiting before a goal gathers, nearest first.
     *
     * <p>Storage is only visited when what the player carries cannot make the goal, so a plan that
     * only crafts never makes a detour. Without Baritone only a chest already within reach counts,
     * because walking to one would be a new reason to pause.
     */
    List<BlockPos> worthVisiting(String item, int wanted, Map<String, Integer> carried) {
        if (chests.isEmpty() || g.client.player == null || g.client.level == null
                || g.client.level.dimension() != Level.OVERWORLD) return List.of();
        List<CraftingPlan.Step> plan = CraftingPlan.plan(item, wanted, carried, GrindBook.recipes());
        if (plan.stream().noneMatch(step -> step.kind() == CraftingPlan.Kind.GATHER)) return List.of();
        boolean travel = g.useBaritone && g.baritone.available();
        Vec3 player = g.client.player.position();
        List<BlockPos> visit = new ArrayList<>();
        for (BlockPos chest : chests) {
            boolean near = travel
                    ? player.distanceToSqr(Vec3.atCenterOf(chest)) <= WITHDRAW_RANGE * WITHDRAW_RANGE
                    : g.reach.withinReach(chest);
            if (!near) continue;
            List<GrindWithdrawal.Stored> contents = seen.get(chest);
            if (contents == null || !GrindWithdrawal.plan(item, wanted, carried, contents, GrindBook.recipes()).isEmpty()) {
                visit.add(chest);
            }
        }
        visit.sort(Comparator.comparingDouble(chest -> player.distanceToSqr(Vec3.atCenterOf(chest))));
        return List.copyOf(visit);
    }
}
