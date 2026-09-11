package me.mrhakan.agalarhack.module.combat;


import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.services.TargetService;
import me.mrhakan.agalarhack.services.TargetRotation;
import me.mrhakan.agalarhack.services.RotationService;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

public class Aura extends Module {

	private int cooldown;
	/**
	 * Rebuilt on enable so a changed switch delay takes effect without a reconnect, and cleared with
	 * the module so a held target cannot survive being switched off and on.
	 */
	private TargetRotation rotation;
	/** Local tick counter: the switch delay is about elapsed ticks, not wall-clock time. */
	private long tick;

	public Aura() {
		super("Aura", Category.COMBAT, "Attacks the best valid entity in range using global and module target filters");
	}

	@Override
	public void selfSettings() {
        TargetService.registerFilters(this);
        addChoiceSetting("rotation", "NONE", "Optional visible aim assistance through the shared rotation service", "NONE", "CLIENT", "SMOOTH");
        addNumberSetting("rotationSpeed", 180, 1, 720, "Smooth rotation speed in degrees per second");
        addNumberSetting("maxYawStep", 45, 1, 180, "Maximum yaw movement per tick");
        addNumberSetting("maxPitchStep", 30, 1, 90, "Maximum pitch movement per tick");
        addBooleanSetting("returnRotation", false, "Restore starting view on release if the user has not moved it");
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
		addNumberSetting("switchDelay", 8.0, 0.0, 60.0,
				"Ticks a chosen target is kept even when another ranks higher; 0 always takes the top-ranked one");
		addNumberSetting("maxTargets", 1.0, 1.0, 8.0,
				"Spread attacks across this many targets, one per landed hit; 1 is ordinary single-target behaviour");
	}

	@Override
	public void onEnable() {
		cooldown = 0;
		tick = 0;
		rotation = new TargetRotation((int) Math.round(getNumberSetting("switchDelay", 8.0)));
		setDisplayName(null);
	}

	@Override
	public void onDisable() {
        service(RotationService.class).release("aura");
		cooldown = 0;
		if (rotation != null) rotation.reset();
		setDisplayName(null);
	}

	/** A new world or a new connection makes every held entity id meaningless. */
	@Override public void onDisconnect() { if (rotation != null) rotation.reset(); }

	@Override
	public void onWorldChanged(boolean ready) {
		if (rotation != null) rotation.reset();
		super.onWorldChanged(ready);
	}

	@Override
	public void onUpdate() {
		if (mc.player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) {
			setDisplayName(null);
			return;
		}
		tick++;
		if (rotation == null) rotation = new TargetRotation((int) Math.round(getNumberSetting("switchDelay", 8.0)));

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

        var mode = RotationService.Mode.valueOf(getStringSetting("rotation", "NONE"));
        if (mode != RotationService.Mode.NONE) {
            double dx = target.getX() - mc.player.getX(), dz = target.getZ() - mc.player.getZ();
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
            float pitch = (float) -Math.toDegrees(Math.atan2(target.getEyeY() - mc.player.getEyeY(), Math.hypot(dx, dz)));
            service(RotationService.class).request(new RotationService.Request("aura", 50, mode, yaw, pitch,
                    (float) getNumberSetting("rotationSpeed", 180), (float) getNumberSetting("maxYawStep", 45),
                    (float) getNumberSetting("maxPitchStep", 30), getBooleanSetting("returnRotation", false)));
            if (Math.abs(me.mrhakan.agalarhack.services.TargetSelection.wrapDegrees(yaw - mc.player.getYRot())) > 2
                    || Math.abs(pitch - mc.player.getXRot()) > 2) return;
        }
		if (vanillaCooldown) {
			if (mc.player.getAttackStrengthScale(0.5f) < 1.0f) {
				return;
			}
		} else if (cooldown > 0) {
			return;
		}

		mc.gameMode.attack(mc.player, target);
		mc.player.swing(InteractionHand.MAIN_HAND);
		// Only a landed hit advances a multi-target rotation, so each target gets a real attack
		// rather than the rotation running ahead of the attack timing and spraying.
		rotation.attacked(tick);
		if (!vanillaCooldown) {
			cooldown = (int) Math.round(getNumberSetting("delay", 10.0));
		}
	}

	/**
	 * Asks for as many candidates as the rotation can use, then lets it decide which one to hit.
	 *
	 * <p>Priority ordering alone flips between two nearly equal targets as they move, and every flip
	 * throws away the attack charge that was building. TargetRotation holds the current one unless it
	 * is genuinely lost or another has stayed better for the configured delay.
	 */
	private LivingEntity findTarget() {
		int maxTargets = (int) Math.round(getNumberSetting("maxTargets", 1.0));
		var targets = service(TargetService.class).select(this, getNumberSetting("range", 4.2),
				getNumberSetting("wallsRange", 3), getNumberSetting("fov", 360),
				getStringSetting("priority", "closest"), Math.max(1, maxTargets));
		if (targets.isEmpty()) {
			rotation.select(tick, new int[0], maxTargets);
			return null;
		}
		int[] ids = new int[targets.size()];
		for (int index = 0; index < ids.length; index++) ids[index] = targets.get(index).getId();
		int chosen = rotation.select(tick, ids, maxTargets);
		for (LivingEntity candidate : targets) {
			if (candidate.getId() == chosen) return candidate;
		}
		return targets.get(0);
	}
}
