package me.mrhakan.agalarhack.module.combat;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Attacks only the entity under the vanilla crosshair. Unlike Aura this does not
 * search nearby entities or rotate the player, making it a small predictable
 * alternative for users who want manual aim with automatic timing.
 */
public class TriggerBot extends Module {
    private int cooldown;

    public TriggerBot() {
        super("TriggerBot", Category.COMBAT, "Attacks the valid entity under your crosshair when the hit is ready");
    }

    @Override
    public void selfSettings() {
        addBooleanSetting("players", true, "Allow player targets");
        addBooleanSetting("mobs", true, "Allow non-player living targets");
        addBooleanSetting("ignoreFriends", true, "Never attack players in the local friend list");
        addBooleanSetting("ignoreInvisible", true, "Skip invisible targets");
        addBooleanSetting("pauseOnUse", true, "Pause while using an item");
        addBooleanSetting("onlyOnClick", false, "Only trigger while the attack key is held");
        addBooleanSetting("vanillaCooldown", true, "Use Minecraft's normal fully-charged attack timing");
        addNumberSetting("delay", 10.0, 0.0, 40.0, "Custom delay in ticks when vanillaCooldown is off");
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
        if (mc.player == null || mc.gameMode == null) {
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

        if (!(mc.hitResult instanceof EntityHitResult hit)) {
            setDisplayName(null);
            return;
        }
        Entity entity = hit.getEntity();
        if (!(entity instanceof LivingEntity target) || !isValidTarget(target)) {
            setDisplayName(null);
            return;
        }

        setDisplayName("TriggerBot [" + target.getName().getString() + "]");
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

    private boolean isValidTarget(LivingEntity target) {
        if (target == mc.player || !target.isAlive() || target.getHealth() <= 0 || target.isSpectator()) {
            return false;
        }
        if (getBooleanSetting("ignoreInvisible", true) && target.isInvisible()) {
            return false;
        }

        if (target instanceof Player player) {
            if (!getBooleanSetting("players", true)) {
                return false;
            }
            return !getBooleanSetting("ignoreFriends", true)
                    || !AgalarHackClient.FRIEND_MANAGER.isFriend(player.getGameProfile().getName());
        }
        return getBooleanSetting("mobs", true);
    }
}
