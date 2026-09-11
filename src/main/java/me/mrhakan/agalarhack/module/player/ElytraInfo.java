package me.mrhakan.agalarhack.module.player;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.InventoryService;
import me.mrhakan.agalarhack.services.NotificationService;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Information and warnings for elytra flight.
 *
 * <p>Deliberately informational, as the roadmap asks: it warns and counts, and never touches
 * movement. Nothing here makes the player fly differently from vanilla.
 */
public class ElytraInfo extends Module {
    private boolean warnedDurability;
    private boolean warnedFireworks;

    public ElytraInfo() {
        super("ElytraInfo", Category.PLAYER, "Elytra durability and firework warnings plus glide speed; informational only");
        markExperimental();
    }

    @Override
    public void selfSettings() {
        addNumberSetting("durabilityWarning", 20, 1, 200, "Warn when elytra durability drops to this many uses");
        addNumberSetting("fireworkWarning", 4, 0, 64, "Warn when fireworks run this low; 0 disables");
        addBooleanSetting("showSpeed", true, "Show glide speed in the module list while flying");
    }

    @Override
    public void onEnable() { reset(); }

    @Override
    public void onDisable() { reset(); setDisplayName(null); }

    @Override public void onDisconnect() { onDisable(); }

    private void reset() {
        warnedDurability = false;
        warnedFireworks = false;
    }

    @Override
    public void onUpdate() {
        if (mc.player == null) { setDisplayName(null); return; }
        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.is(Items.ELYTRA)) {
            reset();
            setDisplayName(null);
            return;
        }

        int remaining = chest.isDamageableItem() ? chest.getMaxDamage() - chest.getDamageValue() : Integer.MAX_VALUE;
        int fireworks = countFireworks();
        updateDisplay(remaining, fireworks);
        warnDurability(remaining);
        warnFireworks(fireworks);
    }

    private void updateDisplay(int remaining, int fireworks) {
        if (!mc.player.isFallFlying()) {
            setDisplayName("ElytraInfo [" + remaining + " dur, " + fireworks + " fw]");
            return;
        }
        if (!getBooleanSetting("showSpeed", true)) {
            setDisplayName("ElytraInfo [" + remaining + " dur]");
            return;
        }
        var motion = mc.player.getDeltaMovement();
        double blocksPerSecond = Math.hypot(motion.x, motion.z) * 20;
        setDisplayName(String.format(java.util.Locale.ROOT, "ElytraInfo [%.0f b/s, %d dur]", blocksPerSecond, remaining));
    }

    /** Warns once per episode; re-arms when the player swaps to a healthier elytra. */
    private void warnDurability(int remaining) {
        int threshold = (int) Math.round(getNumberSetting("durabilityWarning", 20));
        if (remaining <= threshold && !warnedDurability) {
            warnedDurability = true;
            service(NotificationService.class).publish(NotificationService.Type.WARNING,
                    "Elytra at " + remaining + " uses");
        } else if (remaining > threshold + 5) {
            warnedDurability = false;
        }
    }

    private void warnFireworks(int fireworks) {
        int threshold = (int) Math.round(getNumberSetting("fireworkWarning", 4));
        if (threshold <= 0) return;
        // Only worth warning about while actually gliding; on the ground it is not news.
        if (!mc.player.isFallFlying()) return;
        if (fireworks <= threshold && !warnedFireworks) {
            warnedFireworks = true;
            service(NotificationService.class).publish(NotificationService.Type.WARNING,
                    fireworks == 0 ? "Out of fireworks" : "Fireworks low: " + fireworks);
        } else if (fireworks > threshold + 2) {
            warnedFireworks = false;
        }
    }

    private int countFireworks() {
        int count = 0;
        var inventory = service(InventoryService.class);
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.stackAt(slot);
            if (stack.is(Items.FIREWORK_ROCKET)) count += stack.getCount();
        }
        return count;
    }
}
