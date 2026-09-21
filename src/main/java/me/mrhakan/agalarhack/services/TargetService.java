package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import me.mrhakan.agalarhack.managers.FriendManager;
import me.mrhakan.agalarhack.managers.TargetPolicyManager;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

/** Shared local target selector. Global policy always runs before stricter module settings. */
public final class TargetService {
    private final Minecraft mc;
    private final FriendManager friends;
    private final TargetPolicyManager policy;
    public TargetService(Minecraft mc, FriendManager friends, TargetPolicyManager policy) {
        this.mc = mc; this.friends = friends; this.policy = policy;
    }
    public static void registerFilters(Module module) {
        var s = module.settings;
        s.addBooleanSetting("hostile", true, "Allow hostile mobs");
        s.addBooleanSetting("passive", true, "Allow non-hostile living mobs");
        s.addBooleanSetting("animals", true, "Allow creature-category animals");
        s.addBooleanSetting("ignoreTeams", true, "Protect scoreboard teammates");
        s.addBooleanSetting("ignoreCreative", true, "Protect creative-mode players");
        s.addBooleanSetting("ignoreSleeping", true, "Skip sleeping entities");
        s.addBooleanSetting("ignoreArmorStands", true, "Skip armor stands");
        s.addBooleanSetting("ignoreNamedMobs", false, "Skip custom-named non-player mobs");
        s.addBooleanSetting("ignoreNpcLike", false, "Skip players absent from the player list (heuristic, may hide real players)");
    }
    public boolean allows(LivingEntity target, Module module, double range, double wallsRange, double fov) {
        if (mc.player == null || mc.level == null || target == null || target.level() != mc.level || !policy.allows(target)) return false;
        boolean player = target instanceof Player;
        if (!module.getBooleanSetting(player ? "players" : "mobs", true)) return false;
        if (module.getBooleanSetting("ignoreInvisible", true) && target.isInvisible()) return false;
        if (module.getBooleanSetting("ignoreSleeping", true) && target.isSleeping()) return false;
        if (module.getBooleanSetting("ignoreArmorStands", true) && target instanceof ArmorStand) return false;
        if (player) {
            Player other = (Player) target;
            if (module.getBooleanSetting("ignoreFriends", true) && friends.isFriend(target.getName().getString())) return false;
            if (module.getBooleanSetting("ignoreTeams", true) && mc.player.isAlliedTo(target)) return false;
            if (module.getBooleanSetting("ignoreCreative", true) && other.getAbilities().instabuild) return false;
            if (module.getBooleanSetting("ignoreNpcLike", false)
                    && (mc.getConnection() == null || mc.getConnection().getPlayerInfo(target.getUUID()) == null)) return false;
        } else {
            var category = target.getType().getCategory();
            if (!module.getBooleanSetting(category == MobCategory.MONSTER ? "hostile" : "passive", true)) return false;
            if (category == MobCategory.CREATURE && !module.getBooleanSetting("animals", true)) return false;
            if (module.getBooleanSetting("ignoreNamedMobs", false) && target.hasCustomName()) return false;
        }
        double distance = mc.player.distanceToSqr(target);
        // Distance first avoids expensive visibility raycasts for out-of-range entities.
        if (distance > range * range) return false;
        return TargetSelection.inRange(distance, mc.player.hasLineOfSight(target), range, wallsRange, yawDelta(target), fov);
    }
    private double yawDelta(LivingEntity target) {
        double yaw = Math.toDegrees(Math.atan2(target.getZ() - mc.player.getZ(), target.getX() - mc.player.getX())) - 90;
        return TargetSelection.wrapDegrees(yaw - mc.player.getYRot());
    }
    private TargetSelection.Metrics metrics(LivingEntity target) {
        double yaw = Math.abs(yawDelta(target));
        double dx = target.getX() - mc.player.getX(), dz = target.getZ() - mc.player.getZ();
        double pitch = -Math.toDegrees(Math.atan2(target.getEyeY() - mc.player.getEyeY(), Math.sqrt(dx * dx + dz * dz)));
        return new TargetSelection.Metrics(target.getId(), mc.player.distanceToSqr(target), target.getHealth(),
                target.getArmorValue(), yaw, Math.hypot(yaw, pitch - mc.player.getXRot()), target.hurtTime,
                mc.player.getLastHurtByMob() == target && mc.player.tickCount - mc.player.getLastHurtByMobTimestamp() < 200);
    }
    public List<LivingEntity> select(Module module, double range, double wallsRange, double fov, String priority, int maximum) {
        if (mc.level == null || mc.player == null) return List.of();
        int limit = Math.max(1, Math.min(32, maximum));
        record Candidate(LivingEntity entity, TargetSelection.Metrics metrics) { }
        Comparator<Candidate> order = Comparator.comparing(Candidate::metrics, TargetSelection.comparator(priority));
        List<Candidate> best = new ArrayList<>(limit + 1);
        int considered = 0;
        for (var entity : mc.level.entitiesForRendering()) {
            if (++considered > 4096) break;
            if (!(entity instanceof LivingEntity living) || !allows(living, module, range, wallsRange, fov)) continue;
            best.add(new Candidate(living, metrics(living)));
            best.sort(order);
            if (best.size() > limit) best.remove(best.size() - 1);
        }
        return best.stream().map(Candidate::entity).toList();
    }
}
