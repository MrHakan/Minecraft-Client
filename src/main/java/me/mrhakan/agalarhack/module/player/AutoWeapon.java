package me.mrhakan.agalarhack.module.player;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.InventoryService;
import me.mrhakan.agalarhack.services.ItemScoring;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/** Selects the best hotbar weapon for the entity you are about to hit. */
public class AutoWeapon extends Module {
    private static final String OWNER = "autoweapon";
    /** Above AutoTool so a weapon wins the hotbar when both want it, below AutoEat. */
    private static final int PRIORITY = 45;

    public AutoWeapon() {
        super("AutoWeapon", Category.PLAYER, "Picks the strongest hotbar weapon for the current target");
    }

    @Override
    public void selfSettings() {
        addChoiceSetting("source", "both", "Where the target comes from", "crosshair", "combat", "both");
        addNumberSetting("speedWeight", 0.35, 0, 1, "0 favours damage per hit, 1 favours sustained damage per second");
        addNumberSetting("minDurability", 5, 0, 1000, "Skip damageable weapons with this many or fewer uses remaining");
        addNumberSetting("targetTimeout", 2.0, 0.5, 10.0, "Seconds a combat target stays current after it was last seen");
        addBooleanSetting("onlyWhileAttacking", false, "Only switch while the attack key is held");
        addBooleanSetting("swapBack", true, "Return to the previous hotbar slot afterwards");
    }

    @Override public void onEnable() { setDisplayName(null); }

    @Override
    public void onDisable() {
        service(InventoryService.class).release(OWNER);
        setDisplayName(null);
    }

    @Override public void onDisconnect() { onDisable(); }

    @Override
    public void onUpdate() {
        InventoryService inventory = service(InventoryService.class);
        if (mc.player == null || mc.level == null || mc.gui.screen() != null
                || (getBooleanSetting("onlyWhileAttacking", false) && !mc.options.keyAttack.isDown())) {
            stop(inventory);
            return;
        }
        LivingEntity target = resolveTarget();
        if (target == null) { stop(inventory); return; }

        ItemScoring.TargetFamily family = InventoryService.familyOf(target);
        int slot = inventory.findBestWeapon(family, getNumberSetting("speedWeight", 0.35),
                (int) Math.round(getNumberSetting("minDurability", 5)));
        if (slot < 0) { stop(inventory); return; }
        if (!inventory.select(OWNER, PRIORITY, slot, false, getBooleanSetting("swapBack", true))) {
            stop(inventory);
            return;
        }
        setDisplayName("AutoWeapon [" + inventory.stackAt(slot).getHoverName().getString() + "]");
    }

    private void stop(InventoryService inventory) {
        inventory.release(OWNER);
        setDisplayName(null);
    }

    private LivingEntity resolveTarget() {
        String source = getStringSetting("source", "both");
        if (!"combat".equalsIgnoreCase(source)
                && mc.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity living
                && living.isAlive() && living != mc.player) {
            return living;
        }
        if ("crosshair".equalsIgnoreCase(source)) return null;
        return AgalarHackClient.TARGET_TRACKER.get(getNumberSetting("targetTimeout", 2.0));
    }
}
