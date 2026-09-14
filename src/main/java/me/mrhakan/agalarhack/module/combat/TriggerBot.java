package me.mrhakan.agalarhack.module.combat;

import java.util.Locale;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.services.CriticalHits;
import me.mrhakan.agalarhack.services.DwellGate;
import me.mrhakan.agalarhack.services.TargetService;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/** Manual-aim automatic attack helper that shares the global target policy. */
public class TriggerBot extends Module {
    private int cooldown;
    /** How long the crosshair has rested on the current target; see the reaction setting. */
    private final DwellGate dwell = new DwellGate();

    public TriggerBot() {
        super("TriggerBot", Category.COMBAT, "Attacks the valid entity under your crosshair when the hit is ready");
    }

    @Override
    public void selfSettings() {
        TargetService.registerFilters(this);
        addBooleanSetting("players", true, "Allow player targets after the global target policy");
        addBooleanSetting("mobs", true, "Allow non-player living targets after the global target policy");
        addBooleanSetting("ignoreFriends", true, "Never attack players in the local friend list");
        addBooleanSetting("ignoreInvisible", true, "Skip invisible targets");
        addBooleanSetting("pauseOnUse", true, "Pause while using an item");
        addBooleanSetting("onlyOnClick", false, "Only trigger while the attack key is held");
        addBooleanSetting("vanillaCooldown", true, "Use Minecraft's normal fully-charged attack timing");
        addNumberSetting("delay", 10.0, 0.0, 40.0, "Custom delay in ticks when vanillaCooldown is off");
        addChoiceSetting("requireWeapon", "any", "Only trigger while holding this kind of item",
                "any", "melee_weapon", "sword");
        addBooleanSetting("requireCritical", false,
                "Hold the attack until it would land as a critical hit, using Minecraft's own 26.2 rule");
        addNumberSetting("reactionTicks", 0.0, 0.0, 20.0,
                "Ticks the crosshair must rest on a target first, so sweeping past something does not attack it");
    }

    @Override
    public void onEnable() {
        cooldown = 0;
        dwell.reset();
        setDisplayName(null);
    }

    @Override
    public void onDisable() {
        cooldown = 0;
        dwell.reset();
        setDisplayName(null);
    }

    /** Held entity ids mean nothing in another world, and nothing at all without a connection. */
    @Override public void onDisconnect() { dwell.reset(); }

    @Override
    public void onWorldChanged(boolean ready) {
        dwell.reset();
        super.onWorldChanged(ready);
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.gameMode == null || mc.gui.screen() != null) {
            stand();
            return;
        }
        if (getBooleanSetting("pauseOnUse", true) && mc.player.isUsingItem()) {
            stand();
            return;
        }
        if (getBooleanSetting("onlyOnClick", false) && !mc.options.keyAttack.isDown()) {
            stand();
            return;
        }
        if (!holdingRequiredItem()) {
            stand();
            return;
        }

        boolean vanillaCooldown = getBooleanSetting("vanillaCooldown", true);
        if (!vanillaCooldown && cooldown > 0) {
            cooldown--;
        }

        if (!(mc.hitResult instanceof EntityHitResult hit)) {
            stand();
            return;
        }
        Entity entity = hit.getEntity();
        if (!(entity instanceof LivingEntity target) || !isValidTarget(target)) {
            stand();
            return;
        }

        AgalarHackClient.TARGET_TRACKER.set(target);
        setDisplayName("TriggerBot [" + target.getName().getString() + "]");
        // Counted before the cooldown checks so the wait runs down while the attack recharges
        // rather than after it, which would add the two delays together.
        boolean settled = dwell.ready(target.getId(), (int) Math.round(getNumberSetting("reactionTicks", 0.0)));
        if (!settled) return;
        if (getBooleanSetting("requireCritical", false) && !wouldCrit()) return;
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

    /** Clears the display name and the dwell count together; every stand-down path uses it. */
    private void stand() {
        setDisplayName(null);
        dwell.reset();
    }

    /**
     * Mirrors {@code Player.canCriticalAttack} in 26.2 rather than an older version's rule: 26.2 has
     * no blindness term and {@code fallDistance} is a double here. Waiting on the wrong condition
     * would hold the attack for a crit that was never coming.
     */
    private boolean wouldCrit() {
        return CriticalHits.wouldCrit(new CriticalHits.State(mc.player.fallDistance, mc.player.onGround(),
                mc.player.onClimbable(), mc.player.isInWater(), mc.player.isMobilityRestricted(),
                mc.player.isPassenger(), mc.player.isSprinting()));
    }

    /** Item tags rather than item classes, since 26.2 describes weapons by tag. */
    private boolean holdingRequiredItem() {
        String requirement = getStringSetting("requireWeapon", "any").toLowerCase(Locale.ROOT);
        if ("any".equals(requirement)) return true;
        var held = mc.player.getMainHandItem();
        if ("sword".equals(requirement)) return held.is(ItemTags.SWORDS);
        return held.is(ItemTags.SWORDS) || held.is(ItemTags.AXES) || held.is(ItemTags.MELEE_WEAPON_ENCHANTABLE);
    }

    private boolean isValidTarget(LivingEntity target) {
        // Vanilla crosshair picking already constrains reach; shared selector adds safety filters.
        return service(TargetService.class).allows(target, this, 6, 0, 360);
    }
}
