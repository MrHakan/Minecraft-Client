package me.mrhakan.agalarhack.module.combat;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

public class Aura extends Module {

	private int cooldown = 0;

	public Aura() {
		super("Aura", Category.COMBAT, "Attacks the closest entity in range");
	}

	@Override
	public void selfSettings() {
		settings.addSetting("range", 4.0);
		settings.addSetting("delay", 10.0);
		settings.addSetting("players", true);
		settings.addSetting("mobs", true);
	}

	@Override
	public void onUpdate() {
		if (cooldown > 0) {
			cooldown--;
			return;
		}

		double range = getNumberSetting("range", 4.0);
		boolean targetPlayers = getBooleanSetting("players", true);
		boolean targetMobs = getBooleanSetting("mobs", true);

		LivingEntity target = null;
		double closestSq = range * range;

		for (Entity entity : mc.world.getEntities()) {
			if (!(entity instanceof LivingEntity living) || entity == mc.player) {
				continue;
			}
			if (!living.isAlive() || living.getHealth() <= 0 || living.isSpectator()) {
				continue;
			}
			boolean isPlayer = living instanceof PlayerEntity;
			if (isPlayer && !targetPlayers) {
				continue;
			}
			if (!isPlayer && !targetMobs) {
				continue;
			}
			double distanceSq = mc.player.squaredDistanceTo(living);
			if (distanceSq <= closestSq) {
				closestSq = distanceSq;
				target = living;
			}
		}

		if (target != null) {
			mc.interactionManager.attackEntity(mc.player, target);
			mc.player.swingHand(Hand.MAIN_HAND);
			cooldown = (int) getNumberSetting("delay", 10.0);
		}
	}
}
