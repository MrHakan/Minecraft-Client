package me.mrhakan.agalarhack.module.render;

import java.util.List;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.EntityDiscovery;
import me.mrhakan.agalarhack.services.ScannerService;
import me.mrhakan.agalarhack.services.scanning.ScanScheduler;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Readable nametags above players and mobs.
 *
 * <p>The roadmap explicitly warns against text walls, so every field is opt-in, the tag is a single
 * line, and the parts are ordered so the two that matter in a fight - name and health - come first.
 */
public class Nametags extends Module {
    private List<LivingEntity> targets = List.of();

    public Nametags() {
        super("Nametags", Category.RENDER, "Compact single-line nametags with health, armour and held item");
        markExperimental();
    }

    @Override
    public void selfSettings() {
        addNumberSetting("range", 64.0, 8.0, 192.0, "Maximum nametag distance in blocks");
        addNumberSetting("maximumTags", 64, 8, 256, "Maximum rendered nametags; nearest are kept");
        addBooleanSetting("players", true, "Tag players");
        addBooleanSetting("mobs", false, "Tag non-player living entities");
        addBooleanSetting("health", true, "Show current health");
        addBooleanSetting("absorption", true, "Include absorption hearts in the health figure");
        addBooleanSetting("armor", true, "Show armour points");
        addBooleanSetting("distance", false, "Show distance");
        addBooleanSetting("heldItem", true, "Show the held item name");
        addBooleanSetting("durability", false, "Show the held item's remaining durability");
        addBooleanSetting("friendMarker", true, "Mark players in the local friend list");
        addBooleanSetting("hideVanillaTag", false, "Skip entities that already show a vanilla name tag");
    }

    public List<LivingEntity> targets() { return targets; }

    @Override
    public void onDisable() {
        service(ScannerService.class).cancel(this);
        targets = List.of();
    }

    @Override
    public void onUpdate() {
        var player = mc.player;
        if (player == null) { targets = List.of(); return; }
        boolean players = getBooleanSetting("players", true);
        boolean mobs = getBooleanSetting("mobs", false);
        boolean skipNamed = getBooleanSetting("hideVanillaTag", false);
        EntityDiscovery.offer(this, service(ScannerService.class), ScanScheduler.Priority.NEAR,
                mc, (int) getNumberSetting("maximumTags", 64), getNumberSetting("range", 64),
                entity -> {
                    if (!(entity instanceof LivingEntity living) || living == player || !living.isAlive()) return null;
                    if (!(living instanceof Player ? players : mobs)) return null;
                    if (skipNamed && living.isCustomNameVisible()) return null;
                    return living;
                },
                result -> targets = result);
    }

    /** Builds the single line shown above an entity. Kept here so the renderer stays layout-only. */
    public String label(LivingEntity entity, double distance, boolean friend) {
        StringBuilder line = new StringBuilder();
        if (friend && getBooleanSetting("friendMarker", true)) line.append("★ ");
        line.append(entity.getName().getString());
        if (getBooleanSetting("health", true)) {
            float health = entity.getHealth();
            if (getBooleanSetting("absorption", true)) health += entity.getAbsorptionAmount();
            line.append(' ').append(Math.round(health));
        }
        if (getBooleanSetting("armor", true)) {
            int armor = entity.getArmorValue();
            if (armor > 0) line.append(" [").append(armor).append(']');
        }
        if (getBooleanSetting("heldItem", true)) {
            ItemStack held = entity.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!held.isEmpty()) {
                line.append(' ').append(held.getHoverName().getString());
                if (getBooleanSetting("durability", false) && held.isDamageableItem()) {
                    line.append(' ').append(held.getMaxDamage() - held.getDamageValue());
                }
            }
        }
        if (getBooleanSetting("distance", false)) line.append(' ').append(Math.round(distance)).append('m');
        return line.toString();
    }
}
