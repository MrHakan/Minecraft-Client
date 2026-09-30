package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** World-aware tasks for the survival campaign. All clicks and aim use existing shared services. */
final class SurvivalTasks {
    static final String OWNER = "autogrind";
    static final int PRIORITY = 50;
    final GrindExecutor grind;
    final SurvivalProgression.Tier tier;
    final BlockPos base;
    BlockPos netherEntry;
    boolean expectingDimension;
    boolean eating;
    int eatingTicks;
    List<BlockPos> storageChests = List.of();
    TaskRunner storageWork;
    final List<SurvivalProgression.Goal> taskGoals = new ArrayList<>();

    SurvivalTasks(GrindExecutor grind, SurvivalProgression.Tier tier, BlockPos base) {
        this.grind = grind; this.tier = tier; this.base = base.immutable();
    }
    List<TaskRunner.Task> tasks() {
        List<TaskRunner.Task> tasks = new ArrayList<>();
        taskGoals.clear();
        for (SurvivalProgression.Goal goal : SurvivalProgression.goals(tier)) {
            switch (goal.kind()) {
                case ITEM -> { tasks.add(new ItemGoalTask(grind, goal.item(), goal.count(), true)); tasks.add(new EquipTask(grind)); }
                case FOOD -> tasks.add(new FoodTask(grind, goal.count()));
                case HOUSE -> tasks.add(new BuildTask(grind, base, SurvivalBlueprint.house(goal.count() > 1), goal.item()));
                case STORAGE -> {
                    List<BlockPos> chests = goal.count() == 1 ? List.of(base.offset(1, 1, 3))
                            : List.of(base.offset(7, 1, 5), base.offset(9, 1, 5), base.offset(11, 1, 5));
                    tasks.add(new StorageTask(grind, chests, Map.of(), true));
                }
                case PORTAL -> tasks.add(new PortalTask(grind, base.offset(-5, 0, 0)));
                case NETHER -> tasks.add(new DimensionTask(true));
                case OVERWORLD -> tasks.add(new DimensionTask(false));
                case ENCHANT -> { tasks.add(new EnchantTask(grind, base.offset(8, 0, 10))); tasks.add(new EquipTask(grind)); }
            }
            while (taskGoals.size() < tasks.size()) taskGoals.add(goal);
        }
        return List.copyOf(tasks);
    }

    /** Eating yields aim/mining control and never consumes golden apples or poisonous food. */
    boolean maintain() {
        if (grind.client.player.getHealth() <= 6 || grind.client.player.isOnFire()) {
            grind.pause("Low health or fire: reach safety and recover, then .grind resume.");
            return true;
        }
        if (grind.client.gui.screen() != null) return maintainInventory();
        if (grind.client.player.getFoodData().getFoodLevel() > 14 && !eating) return maintainInventory();
        if (!grind.client.player.getFoodData().needsFood()) { grind.inventory.release(OWNER); eating = false; eatingTicks = 0; return maintainInventory(); }
        int source = grind.inventory.findInventory(stack -> safeFood(id(stack)));
        if (source < 0) {
            eating = false; eatingTicks = 0; grind.inventory.release(OWNER);
            if (grind.client.player.getFoodData().getFoodLevel() <= 6) {
                grind.pause("Food exhausted: collect food, then .grind resume."); return true;
            }
            return maintainInventory();
        }
        eating = true;
        if (++eatingTicks > 200) { grind.pause("Eating did not complete; check food access, then .grind resume."); eating = false; eatingTicks = 0; return true; }
        grind.cancelAutomation(); grind.rotations.release(OWNER);
        int hotbar = grind.prepareHotbarItem(id(grind.inventory.stackAt(source)));
        if (hotbar < 0) return true;
        grind.inventory.select(OWNER, 60, hotbar, true, true);
        return true;
    }
    private int freeSlots() {
        int free = 0;
        for (int slot = 0; slot < InventoryTransfers.INVENTORY_SIZE; slot++)
            if (grind.inventory.stackAt(slot).isEmpty()) free++;
        return free;
    }
    private boolean maintainInventory() {
        if (storageWork == null) {
            if (freeSlots() >= 2 || grind.inventory.transfers().busy()) return false;
            if (grind.client.gui.screen() != null && !grind.closeOwnedStationMenu()) return false;
            grind.cancelAutomation();
            if (storageChests.isEmpty() || grind.client.level.dimension() != Level.OVERWORLD) {
                grind.pause("Inventory nearly full: free two slots, then .grind resume. Base storage is not built yet or unavailable in this dimension.");
                return true;
            }
            storageWork = new TaskRunner();
            storageWork.start(List.of(new StorageTask(grind, storageChests, pressureReserves())));
        }
        if (storageWork.state() == TaskRunner.State.NEEDS_MOVEMENT) storageWork.resume();
        storageWork.tick();
        if (storageWork.state() == TaskRunner.State.NEEDS_MOVEMENT) grind.pause(storageWork.blockedReason());
        else if (storageWork.state() == TaskRunner.State.FAILED) {
            String reason = storageWork.failure(); storageWork = null;
            grind.pause("Base storage failed: " + reason + "; resolve access and .grind resume.");
        } else if (storageWork.state() == TaskRunner.State.DONE) {
            storageWork = null;
            if (freeSlots() < 2) grind.pause("Storage could not free two slots while preserving campaign supplies; free space manually, then .grind resume.");
        }
        return true;
    }
    private Map<String, Integer> pressureReserves() {
        Map<String, Integer> remaining = GrindStoragePolicy.remainingReserves(taskGoals, grind.completed(),
                goal -> gearSatisfied(grind, goal.item(), goal.count()));
        if (tier == SurvivalProgression.Tier.MAX) {
            // Debris and diamonds gathered earlier are still needed by later smithing/copying.
            int templates = grind.count("netherite_upgrade_smithing_template");
            if (taskGoals.subList(Math.min(grind.completed(), taskGoals.size()), taskGoals.size()).stream()
                    .anyMatch(goal -> goal.item().equals("netherite_upgrade_smithing_template")))
                remaining.merge("diamond", Math.max(0, 8 - Math.max(1, templates)) * 7, Math::max);
            remaining.merge("lapis_lazuli", 24, Math::max);
        }
        grind.campaignReserves.forEach((item, count) -> remaining.merge(item, count, Math::max));
        return Map.copyOf(remaining);
    }
    void cancel() { if (storageWork != null) storageWork.cancel(); storageWork = null; }
    static String id(ItemStack stack) { return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(); }
    static boolean safeFood(String item) {
        return Set.of("bread", "cooked_beef", "cooked_porkchop", "cooked_mutton", "cooked_chicken",
                "cooked_cod", "cooked_salmon", "baked_potato", "carrot", "apple", "sweet_berries").contains(item);
    }
    static BlockPos freeStationPosition(GrindExecutor g) {
        BlockPos feet = g.client.player.blockPosition();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = feet.relative(direction);
            if (!g.client.level.getBlockState(pos).isAir() || !g.client.level.getBlockState(pos.above()).isAir()
                    || !g.client.level.getFluidState(pos).isEmpty()
                    || g.client.level.getBlockState(pos.below()).getCollisionShape(g.client.level, pos.below()).isEmpty()) continue;
            return pos;
        }
        return null;
    }
    static boolean hunted(String item) { return Set.of("beef", "porkchop", "chicken", "mutton", "leather").contains(item); }
    static boolean gearSatisfied(GrindExecutor g, String item, int count) {
        if (g.count(item) >= count) return true;
        String[] bits = item.split("_", 2);
        if (count != 1 || bits.length != 2) return false;
        List<String> tiers = List.of("wooden", "stone", "iron", "diamond", "netherite");
        int current = tiers.indexOf(bits[0]);
        if (current < 0) return false;
        for (int i = current + 1; i < tiers.size(); i++) if (g.count(tiers.get(i) + "_" + bits[1]) > 0) return true;
        return false;
    }

    static class ItemGoalTask implements TaskRunner.Task {
        final GrindExecutor g;
        final String item;
        final int wanted;
        final boolean allowUpgrade;
        TaskRunner work;
        int replans;
        String blocked;
        String failure;
        ItemGoalTask(GrindExecutor g, String item, int wanted, boolean allowUpgrade) {
            this.g = g; this.item = item; this.wanted = wanted; this.allowUpgrade = allowUpgrade;
        }
        public String name() { return "get " + wanted + " " + item + (work != null && work.currentTask() != null ? ": " + work.currentTask() : ""); }
        public boolean satisfied() {
            boolean done = !g.inventory.transfers().owns(OWNER)
                    && (allowUpgrade ? gearSatisfied(g, item, wanted) : g.count(item) >= wanted);
            if (done && work != null) { work.cancel(); work = null; g.cancelAutomation(); g.closeOwnedStationMenu(); }
            return done;
        }
        public int budgetTicks() { return 240_000; }
        public boolean tick() {
            blocked = null;
            g.campaignReserves = GrindStoragePolicy.reservesFor(item, wanted);
            if (work == null || work.state() == TaskRunner.State.DONE) {
                Map<String, Integer> have = g.planningInventory(item);
                work = new TaskRunner();
                work.start(g.createTasks(GrindExecutionPlan.plan(item, wanted, have,
                        g.findNearbyBlock(Blocks.CRAFTING_TABLE) != null, g.findNearbyBlock(Blocks.FURNACE) != null), have));
            }
            if (work.state() == TaskRunner.State.NEEDS_MOVEMENT) work.resume();
            work.tick();
            if (work.state() == TaskRunner.State.NEEDS_MOVEMENT) blocked = work.blockedReason();
            if (work.state() == TaskRunner.State.FAILED) {
                failure = work.failure();
                if (failure != null && failure.contains("no carried pickaxe") && ++replans <= 3) {
                    work.cancel(); work = null; failure = null; return true;
                }
                return false;
            }
            return true;
        }
        public String blockedReason() { return blocked; }
        public String failureReason() { return failure; }
        public void cancel() { if (work != null) work.cancel(); g.cancelAutomation(); g.closeOwnedStationMenu(); }
    }

    static final class EquipTask implements TaskRunner.Task {
        final GrindExecutor g;
        EquipTask(GrindExecutor g) { this.g = g; }
        int candidate() {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                int index = g.inventory.findBestArmor(slot, 0, true, false);
                if (index >= 0) return index;
            }
            if (!g.inventory.equipped(EquipmentSlot.OFFHAND).is(net.minecraft.world.item.Items.SHIELD))
                return g.inventory.findInventory(s -> s.is(net.minecraft.world.item.Items.SHIELD));
            return -1;
        }
        public String name() { return "equip upgrades and shield"; }
        public boolean satisfied() { return candidate() < 0 && !g.inventory.transfers().owns(OWNER); }
        public boolean tick() {
            if (g.closeOwnedStationMenu() || g.inventory.transfers().busy()) return true;
            int source = candidate(); if (source < 0) return true;
            EquipmentSlot slot = InventoryService.equipmentSlotOf(g.inventory.stackAt(source));
            int target = switch (slot == null ? EquipmentSlot.OFFHAND : slot) {
                case HEAD -> 5; case CHEST -> 6; case LEGS -> 7; case FEET -> 8; default -> 45;
            };
            g.inventory.transfers().begin(OWNER, PRIORITY, InventoryTransfers.equipPlan(source, target), 0);
            return true;
        }
        public void cancel() { g.inventory.transfers().release(OWNER); }
    }

    static final class FoodTask implements TaskRunner.Task {
        final GrindExecutor g; final int wanted;
        ItemGoalTask work;
        FoodTask(GrindExecutor g, int wanted) { this.g = g; this.wanted = wanted; }
        int foodCount() { int n = 0; for (int i = 0; i < 36; i++) {
            ItemStack s = g.inventory.stackAt(i); if (safeFood(id(s))) n += s.getCount();
        } return n; }
        public String name() { return "food reserve " + foodCount() + "/" + wanted + (work == null ? "" : " — " + work.name()); }
        public boolean satisfied() { boolean done = foodCount() >= wanted && !g.inventory.transfers().owns(OWNER); if (done && work != null) { work.cancel(); work = null; } return done; }
        public int budgetTicks() { return 70_000; }
        public boolean tick() {
            if (work == null || work.satisfied()) {
                String raw = "beef";
                for (String food : List.of("beef", "porkchop", "mutton", "chicken")) if (g.count(food) > 0) { raw = food; break; }
                if (g.count("wheat") >= 3) work = new ItemGoalTask(g, "bread", g.count("bread") + wanted - foodCount(), false);
                else work = new ItemGoalTask(g, "cooked_" + raw, g.count("cooked_" + raw) + wanted - foodCount(), false);
            }
            return work.tick();
        }
        public String blockedReason() { return work == null ? null : work.blockedReason(); }
        public String failureReason() { return work == null ? null : work.failureReason(); }
        public void cancel() { if (work != null) work.cancel(); }
    }

    static final class HuntTask implements TaskRunner.Task {
        final GrindExecutor g; final String item; final int wanted;
        LivingEntity target;
        BlockPos drop;
        int wait;
        String blocked;
        HuntTask(GrindExecutor g, String item, int wanted) { this.g = g; this.item = item; this.wanted = wanted; }
        public String name() { return "hunt " + item + " " + g.count(item) + "/" + wanted; }
        public boolean satisfied() { if (g.count(item) < wanted) return false; g.cancelMovement(); return true; }
        public int budgetTicks() { return 24_000; }
        boolean matches(LivingEntity e) {
            if (!e.isAlive() || e.hasCustomName() || !(e instanceof Animal a) || a.isBaby()) return false;
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            return switch (item) { case "beef", "leather" -> type.equals("cow"); case "porkchop" -> type.equals("pig");
                case "mutton" -> type.equals("sheep"); default -> type.equals("chicken"); };
        }
        public boolean tick() {
            blocked = null;
            if (g.closeOwnedStationMenu()) return true;
            if (g.client.gui.screen() != null) { blocked = "Close the open screen before hunting."; return true; }
            if (target != null && !target.isAlive()) { drop = target.blockPosition(); target = null; wait = 0; }
            if (drop != null) {
                if (g.client.player.position().distanceToSqr(Vec3.atCenterOf(drop)) > 2.5) {
                    if (!g.moveNear(drop)) blocked = g.movementProblem("Walk over animal drops at " + GrindExecutor.coordinates(drop) + ", then .grind resume.");
                    return true;
                }
                g.cancelMovement();
                if (++wait < 40) return true;
                drop = null;
            }
            if (target == null || !matches(target)) {
                target = null; double nearest = 64 * 64;
                for (var entity : g.client.level.entitiesForRendering()) if (entity instanceof LivingEntity e && matches(e)) {
                    double d = e.distanceToSqr(g.client.player); if (d < nearest) { nearest = d; target = e; }
                }
                if (target == null) { blocked = "No loaded animals for " + item + "; travel to animals or supply food, then .grind resume."; return true; }
            }
            if (g.client.player.getEyePosition().distanceToSqr(target.getBoundingBox().getCenter()) > 9
                    || !g.client.player.hasLineOfSight(target)) {
                if (!g.moveNear(target.blockPosition())) blocked = g.movementProblem("Move within sight of the animal at " + GrindExecutor.coordinates(target.blockPosition()) + ".");
                return true;
            }
            g.cancelMovement();
            int weapon = g.inventory.findBestWeapon(ItemScoring.TargetFamily.GENERIC, 0.5, 1);
            if (weapon >= 0 && !g.inventory.select(OWNER, PRIORITY, weapon, false, true)) return true;
            g.aimAt(target.getBoundingBox().getCenter());
            if (!g.aimedAt(target.getBoundingBox().getCenter()) || g.client.player.getAttackStrengthScale(0.5f) < 1) return true;
            g.client.gameMode.attack(g.client.player, target); g.client.player.swing(InteractionHand.MAIN_HAND);
            return true;
        }
        public String blockedReason() { return blocked; }
        public void cancel() { g.cancelMovement(); g.rotations.release(OWNER); g.inventory.release(OWNER); }
    }

    static final class BuildTask implements TaskRunner.Task {
        final GrindExecutor g; final BlockPos origin; final List<SurvivalBlueprint.Placement> blueprint; final String label;
        int index; PlaceTask placement; ItemGoalTask supply; String blocked;
        BuildTask(GrindExecutor g, BlockPos origin, List<SurvivalBlueprint.Placement> blueprint, String label) {
            this.g = g; this.origin = origin; this.blueprint = blueprint; this.label = label;
        }
        public String name() { return "build " + label + " " + index + "/" + blueprint.size(); }
        public boolean satisfied() { return index >= blueprint.size() && !g.inventory.transfers().owns(OWNER); }
        public int budgetTicks() { return 180_000; }
        public boolean tick() {
            blocked = null;
            // Nested callers can revisit a finished build while their own menu clicks settle.
            if (index >= blueprint.size()) return true;
            if (supply != null) {
                if (!supply.satisfied()) { if (!supply.tick()) return false; blocked = supply.blockedReason(); return true; }
                supply = null; g.closeOwnedStationMenu();
            }
            var next = blueprint.get(index);
            if (placement == null) placement = new PlaceTask(g, origin.offset(next.x(), next.y(), next.z()), next.item());
            if (placement.satisfied()) { index++; placement = null; return true; }
            if (g.count(next.item()) < 1) {
                // Small live batches avoid filling inventory with an entire building's materials.
                int remaining = (int) blueprint.subList(index, blueprint.size()).stream().filter(p -> p.item().equals(next.item())).count();
                supply = new ItemGoalTask(g, next.item(), Math.min(32, remaining), false); return true;
            }
            boolean result = placement.tick(); blocked = placement.blockedReason(); return result;
        }
        public String blockedReason() { return blocked; }
        public String failureReason() { return supply != null ? supply.failureReason() : placement == null ? null : placement.failureReason(); }
        public void cancel() { if (supply != null) supply.cancel(); if (placement != null) placement.cancel(); }
    }

    static final class PlaceTask implements TaskRunner.Task {
        final GrindExecutor g; final BlockPos pos; final String item;
        int confirm; int wait; int attempts; int hotbarBefore = -1;
        boolean issued; String blocked; String failure;
        PlaceTask(GrindExecutor g, BlockPos pos, String item) { this.g = g; this.pos = pos.immutable(); this.item = item; }
        boolean matches() {
            String actual = BuiltInRegistries.BLOCK.getKey(g.client.level.getBlockState(pos).getBlock()).getPath();
            return item.equals(GrindBook.generic(actual)) || item.equals(actual) || item.equals("torch") && actual.equals("wall_torch");
        }
        public String name() { return "place " + item + " at " + GrindExecutor.coordinates(pos); }
        public boolean satisfied() { boolean done = matches() && (!issued || confirm >= 5); if (done) g.protect(pos); return done; }
        public boolean tick() {
            blocked = null;
            if (matches()) { confirm++; g.cancelMovement(); g.inventory.release(OWNER); g.rotations.release(OWNER); return true; }
            confirm = 0;
            if (!g.loaded(pos)) { if (!g.moveNear(pos)) blocked = g.movementProblem("Load the build plot at " + GrindExecutor.coordinates(pos) + "."); return true; }
            if (g.closeOwnedStationMenu() || g.inventory.transfers().busy()) return true;
            if (g.client.gui.screen() != null) { blocked = "Close the open screen before building."; return true; }
            if (issued) {
                if (++wait < 40) return true;
                issued = false; attempts++; hotbarBefore = -1;
                if (attempts >= 4) { blocked = "Placement rejected at " + GrindExecutor.coordinates(pos) + "; clear access and .grind resume."; attempts = 0; return true; }
            }
            if (!g.client.level.getBlockState(pos).canBeReplaced() || !g.client.level.getFluidState(pos).isEmpty()) {
                blocked = "Build plot occupied at " + GrindExecutor.coordinates(pos) + "; clear it, then .grind resume."; return true;
            }
            if (g.client.player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos))) {
                if (!g.moveNear(pos)) blocked = g.movementProblem("Step out of placement at " + GrindExecutor.coordinates(pos) + "."); return true;
            }
            BlockHitResult hit = placementHit();
            if (hit == null) { if (!g.moveNear(pos)) blocked = g.movementProblem("Move to a visible solid support beside " + GrindExecutor.coordinates(pos) + "."); return true; }
            g.cancelMovement();
            int hotbar = g.prepareHotbarItem(item);
            if (hotbar == -2) return true;
            if (hotbar < 0) { failure = "missing " + item; return false; }
            if (!g.inventory.select(OWNER, PRIORITY, hotbar, false, true)) return true;
            if (hotbarBefore != hotbar) { hotbarBefore = hotbar; return true; }
            g.aimAt(hit.getLocation()); if (!g.aimedAt(hit.getLocation())) return true;
            g.client.gameMode.useItemOn(g.client.player, InteractionHand.MAIN_HAND, hit);
            issued = true; wait = 0;
            return true;
        }
        BlockHitResult placementHit() {
            Direction[] directions = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};
            for (Direction direction : directions) {
                if ((item.equals("oak_door") || item.equals("torch")) && direction != Direction.DOWN) continue;
                BlockPos support = pos.relative(direction);
                var state = g.client.level.getBlockState(support);
                if (state.getCollisionShape(g.client.level, support).isEmpty()
                        || state.is(Blocks.CHEST) || state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.FURNACE)
                        || state.is(Blocks.ENCHANTING_TABLE) || state.is(Blocks.SMITHING_TABLE)) continue;
                Vec3 face = Vec3.atCenterOf(support).add(direction.getOpposite().getStepX() * 0.5,
                        direction.getOpposite().getStepY() * 0.5, direction.getOpposite().getStepZ() * 0.5);
                if (g.client.player.getEyePosition().distanceToSqr(face) > 4.5 * 4.5) continue;
                var sight = g.client.level.clip(new net.minecraft.world.level.ClipContext(g.client.player.getEyePosition(),
                        face.add(Vec3.atCenterOf(support).subtract(face).scale(0.001)), net.minecraft.world.level.ClipContext.Block.OUTLINE,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, g.client.player));
                if (sight instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(support)
                        && blockHit.getDirection() == direction.getOpposite())
                    return new BlockHitResult(face, direction.getOpposite(), support, false);
            }
            return null;
        }
        public String blockedReason() { return blocked; }
        public String failureReason() { return failure; }
        public void cancel() { g.cancelMovement(); g.inventory.release(OWNER); g.rotations.release(OWNER); }
    }

    /** Binds only the specific opened block and records the menu before any transfer. */
    static final class OpenTask implements TaskRunner.Task {
        final GrindExecutor g; final BlockPos pos; final Class<?> menuType;
        boolean issued; int wait; String blocked;
        OpenTask(GrindExecutor g, BlockPos pos, Class<?> menuType) { this.g = g; this.pos = pos; this.menuType = menuType; }
        public String name() { return "open station at " + GrindExecutor.coordinates(pos); }
        public boolean satisfied() {
            if (!issued || !menuType.isInstance(g.client.player.containerMenu)) return false;
            g.ownMenu(); return true;
        }
        public boolean tick() {
            blocked = null;
            if (g.inventory.transfers().busy()) return true;
            if (issued) {
                if (++wait < 40) return true;
                issued = false; blocked = "Station did not open; check access at " + GrindExecutor.coordinates(pos) + "."; return true;
            }
            if (g.closeOwnedStationMenu()) return true;
            if (g.client.gui.screen() != null) { blocked = "Close the open screen before station access."; return true; }
            BlockHitResult hit = g.hitTarget(pos);
            if (!g.withinReach(pos) || hit == null) { if (!g.moveNear(pos)) blocked = g.movementProblem("Move within sight of station at " + GrindExecutor.coordinates(pos) + "."); return true; }
            g.cancelMovement();
            int slot = -1;
            for (int i = 0; i < 9; i++) if (g.inventory.stackAt(i).isEmpty() || !(g.inventory.stackAt(i).getItem() instanceof net.minecraft.world.item.BlockItem)) { slot = i; break; }
            if (slot < 0) { blocked = "Free a hotbar slot to interact with the station."; return true; }
            if (!g.inventory.select(OWNER, PRIORITY, slot, false, true)) return true;
            g.aimAt(pos); if (!g.aimedAt(pos)) return true;
            g.client.gameMode.useItemOn(g.client.player, InteractionHand.MAIN_HAND, hit); issued = true; wait = 0;
            return true;
        }
        public String blockedReason() { return blocked; }
    }

    static final class StorageTask implements TaskRunner.Task {
        final GrindExecutor g; final List<BlockPos> chests; final Map<String, Integer> reserves; int chest;
        final boolean registerBase;
        OpenTask open; String blocked;
        StorageTask(GrindExecutor g, List<BlockPos> chests) { this(g, chests, Map.of()); }
        StorageTask(GrindExecutor g, List<BlockPos> chests, Map<String, Integer> reserves) {
            this(g, chests, reserves, false);
        }
        StorageTask(GrindExecutor g, List<BlockPos> chests, Map<String, Integer> reserves, boolean registerBase) {
            this.g = g; this.chests = List.copyOf(chests); this.reserves = Map.copyOf(reserves); this.registerBase = registerBase;
        }
        public String name() { return "sort surplus into chest " + (chest + 1) + "/" + chests.size(); }
        public boolean satisfied() {
            boolean done = chest >= chests.size() && !g.inventory.transfers().owns(OWNER);
            if (done && registerBase) g.registerStorage(chests);
            return done;
        }
        public int budgetTicks() { return 60_000; }
        public boolean tick() {
            blocked = null;
            if (g.inventory.transfers().busy()) return true;
            if (open == null) open = new OpenTask(g, chests.get(chest), ChestMenu.class);
            if (!open.satisfied()) { open.tick(); blocked = open.blockedReason(); return true; }
            ChestMenu menu = (ChestMenu) g.client.player.containerMenu;
            if (menu.getRowCount() != 3) { blocked = "Separate the storage chests; AutoGrind expects three-row single chests."; return true; }
            for (int index = 0; index < 36; index++) {
                ItemStack stack = g.inventory.stackAt(index); if (stack.isEmpty() || stack.has(DataComponents.CUSTOM_NAME)) continue;
                String item = GrindBook.generic(id(stack));
                if (chests.size() > 1 && GrindStoragePolicy.category(item) != chest) continue;
                int keep = Math.max(GrindStoragePolicy.reserve(item), reserves.getOrDefault(item, 0));
                int excess = reserves.getOrDefault(item, 0) == 0 && obsolete(stack, item) ? stack.getCount() : g.count(item) - keep;
                int amount = Math.min(excess, stack.getCount()); if (amount < 1) continue;
                int destination = -1;
                for (int slot = 0; slot < 27; slot++) {
                    ItemStack stored = menu.getSlot(slot).getItem();
                    if (!stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, stack) && stored.getCount() < stored.getMaxStackSize()) {
                        destination = slot; amount = Math.min(amount, stored.getMaxStackSize() - stored.getCount()); break;
                    }
                }
                if (destination < 0) for (int slot = 0; slot < 27; slot++) if (menu.getSlot(slot).getItem().isEmpty()) { destination = slot; break; }
                if (destination < 0) { blocked = "Storage chest " + (chest + 1) + " is full; free a slot and .grind resume."; return true; }
                int source = InventoryTransfers.menuSlot(index, InventoryTransfers.PlayerMenuLayout.CHEST);
                List<ContainerTransferController.Click> clicks = new ArrayList<>();
                clicks.add(new ContainerTransferController.Click(source, 0));
                for (int n = 0; n < amount; n++) clicks.add(new ContainerTransferController.Click(destination, 1));
                if (amount < stack.getCount()) clicks.add(new ContainerTransferController.Click(source, 0));
                g.inventory.transfers().beginClicks(OWNER, PRIORITY, menu.containerId, clicks.toArray(ContainerTransferController.Click[]::new), 0);
                return true;
            }
            g.closeOwnedStationMenu(); chest++; open = null; return true;
        }
        private boolean obsolete(ItemStack stack, String item) {
            if (stack.get(DataComponents.ENCHANTMENTS) != null && !stack.get(DataComponents.ENCHANTMENTS).isEmpty()) return false;
            if (!GrindExecutor.usable(stack)) return true;
            String[] bits = item.split("_", 2);
            if (bits.length != 2) return false;
            List<String> tiers = List.of("wooden", "stone", "iron", "diamond", "netherite");
            int tier = tiers.indexOf(bits[0]);
            if (tier < 0) return false;
            for (int n = tier + 1; n < tiers.size(); n++) if (g.count(tiers.get(n) + "_" + bits[1]) > 0) return true;
            return false;
        }
        public String blockedReason() { return blocked; }
        public void cancel() { g.inventory.transfers().release(OWNER); g.closeOwnedStationMenu(); g.cancelMovement(); }
    }

    static final class SmithTask implements TaskRunner.Task {
        final GrindExecutor g; final String output; final int wanted; final String diamond;
        PlaceTask place; OpenTask open; int wait; String blocked;
        SmithTask(GrindExecutor g, String output, int wanted) { this.g = g; this.output = output; this.wanted = wanted; this.diamond = output.replace("netherite_", "diamond_"); }
        public String name() { return "smith " + output; }
        public boolean satisfied() { return g.count(output) >= wanted && !g.inventory.transfers().owns(OWNER); }
        public boolean tick() {
            blocked = null;
            if (g.inventory.transfers().busy()) return true;
            if (!(g.client.player.containerMenu instanceof SmithingMenu)) {
                if (g.closeOwnedStationMenu()) return true;
                // Worn diamond armor must be made available as a smithing input.
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    if (GrindExecutor.countStack(g.inventory.equipped(slot), diamond) == 0) continue;
                    int free = g.client.player.getInventory().getFreeSlot();
                    if (free < 0) { blocked = "Free a slot to unequip " + diamond + "."; return true; }
                    int armor = switch (slot) { case HEAD -> 5; case CHEST -> 6; case LEGS -> 7; default -> 8; };
                    g.inventory.transfers().begin(OWNER, PRIORITY, new int[]{armor, InventoryTransfers.menuSlot(free)}, 0); return true;
                }
                BlockPos station = g.findNearbyBlock(Blocks.SMITHING_TABLE);
                if (station == null) {
                    if (place == null) {
                        BlockPos free = freeStationPosition(g);
                        if (free == null) { blocked = "Stand beside a clear solid surface for the smithing table."; return true; }
                        place = new PlaceTask(g, free, "smithing_table");
                    }
                    if (!place.satisfied()) { place.tick(); blocked = place.blockedReason(); return true; }
                    station = place.pos;
                }
                if (open == null || !open.pos.equals(station)) open = new OpenTask(g, station, SmithingMenu.class);
                open.tick(); blocked = open.blockedReason(); return true;
            }
            SmithingMenu menu = (SmithingMenu) g.client.player.containerMenu; g.ownMenu();
            if (GrindExecutor.countStack(menu.getSlot(3).getItem(), output) > 0) {
                g.inventory.transfers().beginClicks(OWNER, PRIORITY, menu.containerId,
                        new ContainerTransferController.Click[]{new ContainerTransferController.Click(3, 0)}, 0); wait = 0; return true;
            }
            String[] inputs = {"netherite_upgrade_smithing_template", diamond, "netherite_ingot"};
            for (int slot = 0; slot < 3; slot++) {
                ItemStack placed = menu.getSlot(slot).getItem();
                if (!placed.isEmpty()) {
                    if (GrindExecutor.countStack(placed, inputs[slot]) == 0) { blocked = "Smithing station contains unrelated inputs; clear them before .grind resume."; return true; }
                    continue;
                }
                String ingredient = inputs[slot]; int source = g.inventory.findInventory(s -> GrindExecutor.countStack(s, ingredient) > 0);
                if (source < 0) { blocked = "Supply " + ingredient + " for " + output + ", then .grind resume."; return true; }
                int from = InventoryTransfers.menuSlot(source, InventoryTransfers.PlayerMenuLayout.SMITHING);
                g.inventory.transfers().beginClicks(OWNER, PRIORITY, menu.containerId,
                        new ContainerTransferController.Click[]{new ContainerTransferController.Click(from, 0),
                        new ContainerTransferController.Click(slot, 1), new ContainerTransferController.Click(from, 0)}, 0);
                return true;
            }
            if (++wait > 40) { blocked = "Vanilla did not produce " + output + "; inspect the smithing recipe."; wait = 0; }
            return true;
        }
        public String blockedReason() { return blocked; }
        public void cancel() { g.inventory.transfers().release(OWNER); g.closeOwnedStationMenu(); g.cancelMovement(); }
    }

    /** One looted template is retained and duplicated through the real vanilla 3x3 recipe. */
    static final class TemplateTask implements TaskRunner.Task {
        static final String TEMPLATE = "netherite_upgrade_smithing_template";
        final GrindExecutor g; final int wanted;
        ItemGoalTask supplies; PlaceTask place; OpenTask open; String blocked; int wait;
        TemplateTask(GrindExecutor g, int wanted) { this.g = g; this.wanted = wanted; }
        public String name() { return "duplicate upgrade templates " + g.count(TEMPLATE) + "/" + wanted; }
        public boolean satisfied() { return g.count(TEMPLATE) >= wanted && !g.inventory.transfers().owns(OWNER); }
        public int budgetTicks() { return 180_000; }
        public boolean tick() {
            blocked = null;
            if (g.inventory.transfers().busy()) return true;
            if (supplies != null) {
                if (!supplies.satisfied()) { boolean result = supplies.tick(); blocked = supplies.blockedReason(); return result; }
                supplies = null;
            }
            if (g.count(TEMPLATE) < 1 && !(g.client.player.containerMenu instanceof net.minecraft.world.inventory.CraftingMenu)) {
                blocked = "Loot one Netherite Upgrade template from a bastion, then .grind resume; AutoGrind will duplicate the rest."; return true;
            }
            if (!(g.client.player.containerMenu instanceof net.minecraft.world.inventory.CraftingMenu menu)) {
                if (g.closeOwnedStationMenu()) return true;
                if (g.count("diamond") < 7) { supplies = new ItemGoalTask(g, "diamond", 7, false); return true; }
                if (g.count("netherrack") < 1) { supplies = new ItemGoalTask(g, "netherrack", 1, false); return true; }
                BlockPos station = g.findNearbyBlock(Blocks.CRAFTING_TABLE);
                if (station == null) {
                    if (g.count("crafting_table") < 1) { supplies = new ItemGoalTask(g, "crafting_table", 1, false); return true; }
                    if (place == null) {
                        BlockPos free = freeStationPosition(g);
                        if (free == null) { blocked = "Stand beside a clear solid surface for the crafting table."; return true; }
                        place = new PlaceTask(g, free, "crafting_table");
                    }
                    if (!place.satisfied()) { place.tick(); blocked = place.blockedReason(); return true; } station = place.pos;
                }
                if (open == null || !open.pos.equals(station)) open = new OpenTask(g, station, net.minecraft.world.inventory.CraftingMenu.class);
                open.tick(); blocked = open.blockedReason(); return true;
            }
            g.ownMenu();
            if (GrindExecutor.countStack(menu.getSlot(0).getItem(), TEMPLATE) > 0) {
                g.inventory.transfers().beginClicks(OWNER, PRIORITY, menu.containerId,
                        new ContainerTransferController.Click[]{new ContainerTransferController.Click(0, 0)}, 0);
                open = null; wait = 0; return true;
            }
            String[] cells = {"diamond", TEMPLATE, "diamond", "diamond", "netherrack", "diamond", "diamond", "diamond", "diamond"};
            for (int cell = 0; cell < 9; cell++) {
                ItemStack input = menu.getSlot(cell + 1).getItem(); String ingredient = cells[cell];
                if (!input.isEmpty()) {
                    if (GrindExecutor.countStack(input, ingredient) == 0) { blocked = "Clear unrelated crafting inputs before copying templates."; return true; }
                    continue;
                }
                int index = g.inventory.findInventory(stack -> GrindExecutor.countStack(stack, ingredient) > 0);
                if (index < 0) { g.closeOwnedStationMenu(); open = null; return true; }
                int from = InventoryTransfers.menuSlot(index, InventoryTransfers.PlayerMenuLayout.CRAFTING_TABLE);
                int amount = g.inventory.stackAt(index).getCount();
                List<ContainerTransferController.Click> clicks = new ArrayList<>();
                clicks.add(new ContainerTransferController.Click(from, 0)); clicks.add(new ContainerTransferController.Click(cell + 1, 1));
                if (amount > 1) clicks.add(new ContainerTransferController.Click(from, 0));
                g.inventory.transfers().beginClicks(OWNER, PRIORITY, menu.containerId, clicks.toArray(ContainerTransferController.Click[]::new), 0);
                return true;
            }
            if (++wait > 40) { blocked = "Template duplication recipe was not accepted; inspect the crafting table."; wait = 0; }
            return true;
        }
        public String blockedReason() { return blocked; }
        public String failureReason() { return supplies == null ? null : supplies.failureReason(); }
        public void cancel() { if (supplies != null) supplies.cancel(); g.inventory.transfers().release(OWNER); g.closeOwnedStationMenu(); }
    }

    static final class PortalTask implements TaskRunner.Task {
        final GrindExecutor g; final BlockPos origin; final BuildTask build;
        int wait; String blocked;
        PortalTask(GrindExecutor g, BlockPos origin) { this.g = g; this.origin = origin; build = new BuildTask(g, origin, SurvivalBlueprint.portal(), "Nether portal"); }
        public String name() { return "build and light Nether portal"; }
        public boolean satisfied() { return g.client.level.getBlockState(origin.offset(1, 1, 0)).is(Blocks.NETHER_PORTAL); }
        public int budgetTicks() { return 60_000; }
        public boolean tick() {
            blocked = null;
            if (!build.satisfied()) { boolean result = build.tick(); blocked = build.blockedReason(); return result; }
            BlockPos bottom = origin.offset(1, 0, 0);
            if (!g.withinReach(bottom) || g.hitTarget(bottom) == null) { if (!g.moveNear(bottom)) blocked = g.movementProblem("Move near the portal frame to light it."); return true; }
            g.cancelMovement();
            int slot = g.prepareHotbarItem("flint_and_steel"); if (slot < 0) return true;
            if (!g.inventory.select(OWNER, PRIORITY, slot, false, true)) return true;
            g.aimAt(bottom); if (!g.aimedAt(bottom)) return true;
            if (++wait % 20 == 0) g.client.gameMode.useItemOn(g.client.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atBottomCenterOf(bottom.above()), Direction.UP, bottom, false));
            return true;
        }
        public String blockedReason() { return blocked; }
        public void cancel() { build.cancel(); }
    }

    final class DimensionTask implements TaskRunner.Task {
        final boolean entering; String blocked; int wait;
        DimensionTask(boolean entering) { this.entering = entering; }
        public String name() { return entering ? "enter Nether" : "return to Overworld"; }
        public boolean satisfied() {
            boolean done = entering ? grind.client.level.dimension() == Level.NETHER : grind.client.level.dimension() == Level.OVERWORLD;
            if (done) { expectingDimension = false; if (entering && netherEntry == null) netherEntry = grind.client.player.blockPosition(); grind.cancelMovement(); }
            return done;
        }
        public int budgetTicks() { return 24_000; }
        public boolean tick() {
            blocked = null; expectingDimension = true;
            BlockPos portal = entering ? base.offset(-4, 1, 0) : netherEntry;
            if (portal == null) { blocked = "Locate a return portal and enter it, then .grind resume."; return true; }
            if (grind.client.player.isOnPortalCooldown()) return true;
            if (!grind.moveTo(portal)) blocked = grind.movementProblem("Enter the portal at " + GrindExecutor.coordinates(portal) + "; Baritone pathing is unavailable.");
            if (++wait > 600 && grind.client.player.blockPosition().closerThan(portal, 2)) {
                blocked = "Stand inside the portal until the dimension changes, then .grind resume."; wait = 0;
            }
            return true;
        }
        public String blockedReason() { return blocked; }
        public void cancel() { expectingDimension = false; grind.cancelMovement(); }
    }

    /** Enchant each unenchanted carried/equipped endgame piece using the vanilla third offer. */
    static final class EnchantTask implements TaskRunner.Task {
        final GrindExecutor g; final BlockPos table; final BuildTask setup;
        OpenTask open; String blocked; int wait; boolean enchantIssued; ItemGoalTask xpWork; StorageTask xpStorage;
        EnchantTask(GrindExecutor g, BlockPos table) {
            this.g = g; this.table = table;
            List<SurvivalBlueprint.Placement> layout = new ArrayList<>();
            // Table and the 15 shelves live outside the annex to keep its storage passage clear.
            layout.add(new SurvivalBlueprint.Placement(0, 0, 0, "enchanting_table"));
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) != 2 && Math.abs(z) != 2 || x == 0 && z == -2) continue;
                layout.add(new SurvivalBlueprint.Placement(x, 0, z, "bookshelf"));
            }
            setup = new BuildTask(g, table, List.copyOf(layout), "enchanting area");
        }
        boolean enchantable(ItemStack s) { return Set.of("netherite_pickaxe", "netherite_axe", "netherite_shovel", "netherite_sword", "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots").contains(id(s)) && !s.has(DataComponents.CUSTOM_NAME)
                && (s.get(DataComponents.ENCHANTMENTS) == null || s.get(DataComponents.ENCHANTMENTS).isEmpty()); }
        int source() { return g.inventory.findInventory(this::enchantable); }
        EquipmentSlot worn() {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
                if (enchantable(g.inventory.equipped(slot))) return slot;
            return null;
        }
        public String name() { return "enchant Netherite gear with level 30 offers"; }
        public boolean satisfied() {
            return setup.satisfied() && source() < 0 && worn() == null && !g.inventory.transfers().owns(OWNER)
                    && (!(g.client.player.containerMenu instanceof EnchantmentMenu m) || m.getSlot(0).getItem().isEmpty());
        }
        public int budgetTicks() { return 180_000; }
        public boolean tick() {
            blocked = null;
            if (!setup.satisfied()) { boolean result = setup.tick(); blocked = setup.blockedReason(); return result; }
            if (xpStorage != null) {
                if (!xpStorage.satisfied()) { boolean result = xpStorage.tick(); blocked = xpStorage.blockedReason(); return result; }
                xpStorage = null;
            }
            if (xpWork != null) {
                if (!xpWork.satisfied()) { boolean result = xpWork.tick(); blocked = xpWork.blockedReason(); return result; }
                xpWork = null;
                if (g.count("coal") > 64) xpStorage = new StorageTask(g, List.of(table.offset(1, 1, -5)));
                return true;
            }
            if (g.inventory.transfers().busy()) return true;
            if (!(g.client.player.containerMenu instanceof EnchantmentMenu menu)) {
                if (g.closeOwnedStationMenu()) return true;
                EquipmentSlot worn = worn();
                if (source() < 0 && worn != null) {
                    int free = g.client.player.getInventory().getFreeSlot();
                    if (free < 0) { blocked = "Free a slot to enchant equipped armor."; return true; }
                    int from = switch (worn) { case HEAD -> 5; case CHEST -> 6; case LEGS -> 7; default -> 8; };
                    g.inventory.transfers().begin(OWNER, PRIORITY, new int[]{from, InventoryTransfers.menuSlot(free)}, 0); return true;
                }
                if (open == null) open = new OpenTask(g, table, EnchantmentMenu.class);
                open.tick(); blocked = open.blockedReason(); return true;
            }
            g.ownMenu();
            ItemStack input = menu.getSlot(0).getItem();
            if (!input.isEmpty() && !enchantable(input)) {
                if (!id(input).startsWith("netherite_")) { blocked = "Enchanting station contains an unrelated item."; return true; }
                g.inventory.transfers().beginClicks(OWNER, PRIORITY, menu.containerId,
                        new ContainerTransferController.Click[]{new ContainerTransferController.Click(0, 0)}, 0);
                enchantIssued = false; wait = 0; return true;
            }
            if (input.isEmpty()) {
                int source = source(); if (source < 0) { g.closeOwnedStationMenu(); open = null; return true; }
                int from = InventoryTransfers.menuSlot(source, InventoryTransfers.PlayerMenuLayout.ENCHANTING);
                g.inventory.transfers().beginClicks(OWNER, PRIORITY, menu.containerId,
                        new ContainerTransferController.Click[]{new ContainerTransferController.Click(from, 0), new ContainerTransferController.Click(0, 0)}, 0);
                return true;
            }
            if (menu.getSlot(1).getItem().getCount() < 3) {
                int lapis = g.inventory.findInventory(s -> id(s).equals("lapis_lazuli"));
                if (lapis < 0) { blocked = "Supply lapis lazuli for enchanting, then .grind resume."; return true; }
                int from = InventoryTransfers.menuSlot(lapis, InventoryTransfers.PlayerMenuLayout.ENCHANTING);
                int amount = Math.min(3 - menu.getSlot(1).getItem().getCount(), g.inventory.stackAt(lapis).getCount());
                List<ContainerTransferController.Click> clicks = new ArrayList<>(); clicks.add(new ContainerTransferController.Click(from, 0));
                for (int i = 0; i < amount; i++) clicks.add(new ContainerTransferController.Click(1, 1));
                if (amount < g.inventory.stackAt(lapis).getCount()) clicks.add(new ContainerTransferController.Click(from, 0));
                g.inventory.transfers().beginClicks(OWNER, PRIORITY, menu.containerId, clicks.toArray(ContainerTransferController.Click[]::new), 0);
                return true;
            }
            if (menu.costs[2] == 0) { if (++wait > 40) blocked = "Check the clear one-block gap around the 15 bookshelves."; return true; }
            if (g.client.player.experienceLevel < menu.costs[2]) {
                g.closeOwnedStationMenu(); open = null;
                xpWork = new ItemGoalTask(g, "coal", g.count("coal") + 32, false);
                return true;
            }
            if (!enchantIssued) { g.client.gameMode.handleInventoryButtonClick(menu.containerId, 2); enchantIssued = true; wait = 0; }
            else if (++wait > 40) { blocked = "Enchant offer was not accepted; inspect the station and .grind resume."; enchantIssued = false; }
            return true;
        }
        public String blockedReason() { return blocked; }
        public void cancel() { setup.cancel(); if (xpWork != null) xpWork.cancel(); if (xpStorage != null) xpStorage.cancel(); g.inventory.transfers().release(OWNER); g.closeOwnedStationMenu(); }
    }
}
