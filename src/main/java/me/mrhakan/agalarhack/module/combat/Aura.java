package me.mrhakan.agalarhack.module.combat;

import java.util.Locale;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.services.TargetService;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class Aura extends Module {

	private int cooldown;

	public Aura() {
		super("Aura", Category.COMBAT, "Attacks the best valid entity in range using global and module target filters");
	}

	@Override
	public void selfSettings() {
        TargetService.registerFilters(this);
		addNumberSetting("range", 4.2, 1.0, 6.0, "Maximum attack range when the target is visible");
		addNumberSetting("wallsRange", 3.0, 0.0, 6.0, "Maximum range for targets without line of sight; 0 disables wall hits");
		addNumberSetting("fov", 360.0, 1.0, 360.0, "Horizontal target field of view in degrees");
		addBooleanSetting("players", true, "Allow player targets after the global target policy");
		addBooleanSetting("mobs", true, "Allow non-player living targets after the global target policy");
		addBooleanSetting("ignoreFriends", true, "Never target players in the local friend list");
		addBooleanSetting("ignoreInvisible", true, "Skip invisible targets");
		addBooleanSetting("pauseOnUse", true, "Pause while using an item");
		addBooleanSetting("onlyOnClick", false, "Only attack while the attack key is held");
		addBooleanSetting("vanillaCooldown", true, "Use Minecraft's normal fully-charged attack timing");
		addNumberSetting("delay", 10.0, 0.0, 40.0, "Custom delay in ticks when vanillaCooldown is off");
		addChoiceSetting("priority", "closest", "How valid targets are prioritized",
				"closest", "lowest_health", "highest_health", "lowest_armor", "angle", "crosshair", "hurt_time", "recent_attacker");
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

		AgalarHackClient.TARGET_TRACKER.set(target);
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
        var targets = service(TargetService.class).select(this, getNumberSetting("range", 4.2),
                getNumberSetting("wallsRange", 3), getNumberSetting("fov", 360), getStringSetting("priority", "closest"), 1);
        return targets.isEmpty() ? null : targets.get(0);
    }
}
