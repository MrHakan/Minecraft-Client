package me.mrhakan.agalarhack.module.combat;

import java.util.Locale;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class Aura extends Module {

	private int cooldown;

	public Aura() {
		super("Aura", Category.COMBAT, "Attacks the best valid entity in range using configurable target filters");
	}

	@Override
	public void selfSettings() {
		addNumberSetting("range", 4.2, 1.0, 6.0, "Maximum attack range when the target is visible");
		addNumberSetting("wallsRange", 3.0, 0.0, 6.0, "Maximum range for targets without line of sight; 0 disables wall hits");
		addNumberSetting("fov", 360.0, 1.0, 360.0, "Horizontal target field of view in degrees");
		addBooleanSetting("players", true, "Allow player targets");
		addBooleanSetting("mobs", true, "Allow non-player living targets");
		addBooleanSetting("ignoreFriends", true, "Never target players in the local friend list");
		addBooleanSetting("ignoreInvisible", true, "Skip invisible targets");
		addBooleanSetting("pauseOnUse", true, "Pause while using an item");
		addBooleanSetting("onlyOnClick", false, "Only attack while the attack key is held");
		addBooleanSetting("vanillaCooldown", true, "Use Minecraft's normal fully-charged attack timing");
		addNumberSetting("delay", 10.0, 0.0, 40.0, "Custom delay in ticks when vanillaCooldown is off");
		addChoiceSetting("priority", "closest", "How valid targets are prioritized",
				"closest", "lowest_health", "highest_health");
	}

	@Override
	public void onEnable() {
		cooldown = 0;
		setDisplayName(null);
	}

	@Override
	public void onDisable() {
		cooldown = 0;
		setDisplayName(null);
	}

	@Override
	public void onUpdate() {
		if (mc.player == null || mc.level == null || mc.gameMode == null) {
			setDisplayName(null);
			return;
		}

		if (getBooleanSetting("pauseOnUse", true) && mc.player.isUsingItem()) {
			setDisplayName(null);
			return;
		}
		if (getBooleanSetting("onlyOnClick", false) && !mc.options.keyAttack.isDown()) {
			setDisplayName(null);
			return;
		}

		boolean vanillaCooldown = getBooleanSetting("vanillaCooldown", true);
		if (!vanillaCooldown && cooldown > 0) {
			cooldown--;
		}

		LivingEntity target = findTarget();
		if (target == null) {
			setDisplayName(null);
			return;
		}

		setDisplayName("Aura [" + target.getName().getString() + "]");

		if (vanillaCooldown) {
			if (mc.player.getAttackStrengthScale(0.5f) < 1.0f) {
				return;
			}
		} else if (cooldown > 0) {
			return;
		}

		mc.gameMode.attack(mc.player, target);
		mc.player.swing(InteractionHand.MAIN_HAND);
		if (!vanillaCooldown) {
			cooldown = (int) Math.round(getNumberSetting("delay", 10.0));
		}
	}

	private LivingEntity findTarget() {
		LivingEntity best = null;
		String priority = getStringSetting("priority", "closest").toLowerCase(Locale.ROOT);

		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living) || !isValidTarget(living)) {
				continue;
			}
			if (best == null || isBetterTarget(living, best, priority)) {
				best = living;
			}
		}
		return best;
	}

	private boolean isValidTarget(LivingEntity living) {
		if (living == mc.player || !living.isAlive() || living.getHealth() <= 0 || living.isSpectator()) {
			return false;
		}

		boolean playerTarget = living instanceof Player;
		if (playerTarget && !getBooleanSetting("players", true)) {
			return false;
		}
		if (!playerTarget && !getBooleanSetting("mobs", true)) {
			return false;
		}
		if (getBooleanSetting("ignoreInvisible", true) && living.isInvisible()) {
			return false;
		}

		if (playerTarget && getBooleanSetting("ignoreFriends", true)) {
			Player player = (Player) living;
			if (AgalarHackClient.FRIEND_MANAGER.isFriend(player.getGameProfile().getName())) {
				return false;
			}
		}

		if (!isInsideFov(living, getNumberSetting("fov", 360.0))) {
			return false;
		}

		double distanceSq = mc.player.distanceToSqr(living);
		double allowedRange = mc.player.hasLineOfSight(living)
				? getNumberSetting("range", 4.2)
				: getNumberSetting("wallsRange", 3.0);
		return allowedRange > 0 && distanceSq <= allowedRange * allowedRange;
	}

	private boolean isBetterTarget(LivingEntity candidate, LivingEntity current, String priority) {
		return switch (priority) {
			case "lowest_health" -> candidate.getHealth() < current.getHealth()
					|| (candidate.getHealth() == current.getHealth()
						&& mc.player.distanceToSqr(candidate) < mc.player.distanceToSqr(current));
			case "highest_health" -> candidate.getHealth() > current.getHealth()
					|| (candidate.getHealth() == current.getHealth()
						&& mc.player.distanceToSqr(candidate) < mc.player.distanceToSqr(current));
			default -> mc.player.distanceToSqr(candidate) < mc.player.distanceToSqr(current);
		};
	}

	private boolean isInsideFov(LivingEntity target, double fov) {
		if (fov >= 360.0) {
			return true;
		}
		double dx = target.getX() - mc.player.getX();
		double dz = target.getZ() - mc.player.getZ();
		double targetYaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
		double delta = wrapDegrees(targetYaw - mc.player.getYRot());
		return Math.abs(delta) <= fov / 2.0;
	}

	private static double wrapDegrees(double degrees) {
		double wrapped = degrees % 360.0;
		if (wrapped >= 180.0) {
			wrapped -= 360.0;
		}
		if (wrapped < -180.0) {
			wrapped += 360.0;
		}
		return wrapped;
	}
}
