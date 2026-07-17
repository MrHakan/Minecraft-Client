package me.mrhakan.agalarhack.module.combat;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;

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

		EntityLivingBase target = null;
		double closestSq = range * range;

		for (Entity entity : mc.world.loadedEntityList) {
			if (!(entity instanceof EntityLivingBase) || entity == mc.player) {
				continue;
			}
			EntityLivingBase living = (EntityLivingBase) entity;
			if (living.isDead || living.getHealth() <= 0) {
				continue;
			}
			boolean isPlayer = living instanceof EntityPlayer;
			if (isPlayer && !targetPlayers) {
				continue;
			}
			if (!isPlayer && !targetMobs) {
				continue;
			}
			double dx = living.posX - mc.player.posX;
			double dy = living.posY - mc.player.posY;
			double dz = living.posZ - mc.player.posZ;
			double distanceSq = dx * dx + dy * dy + dz * dz;
			if (distanceSq <= closestSq) {
				closestSq = distanceSq;
				target = living;
			}
		}

		if (target != null) {
			mc.playerController.attackEntity(mc.player, target);
			mc.player.swingArm(EnumHand.MAIN_HAND);
			cooldown = (int) getNumberSetting("delay", 10.0);
		}
	}
}
