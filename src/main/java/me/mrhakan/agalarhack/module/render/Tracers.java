package me.mrhakan.agalarhack.module.render;

import java.util.List;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.EntityDiscovery;
import me.mrhakan.agalarhack.services.ScannerService;
import me.mrhakan.agalarhack.services.scanning.ScanScheduler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Lines from the screen toward chosen entity groups.
 *
 * <p>ESP already has a tracer toggle for its own targets. This module exists because tracers are
 * often wanted for a different set than the boxes - items while looting, hostiles while travelling -
 * and forcing one filter to serve both made ESP's settings mean two things at once.
 */
public class Tracers extends Module {
    private List<Entity> targets = List.of();

    public Tracers() {
        super("Tracers", Category.RENDER, "Draws lines toward chosen entity groups, independent of ESP filters");
        markExperimental();
    }

    @Override
    public void selfSettings() {
        addNumberSetting("range", 96.0, 8.0, 256.0, "Maximum tracer distance in blocks");
        addNumberSetting("maximumTargets", 64, 8, 256, "Maximum tracers drawn; nearest are kept");
        addChoiceSetting("origin", "center", "Where tracers start on screen", "center", "bottom", "crosshair");
        addBooleanSetting("players", true, "Trace players");
        addBooleanSetting("friends", true, "Trace players in the local friend list");
        addBooleanSetting("hostiles", false, "Trace hostile mobs");
        addBooleanSetting("passives", false, "Trace passive and neutral mobs");
        addBooleanSetting("items", false, "Trace dropped items");
        addBooleanSetting("perGroupColors", true, "Colour each group differently instead of one fixed colour");
        addNumberSetting("red", 255.0, 0.0, 255.0, "Fixed tracer red channel");
        addNumberSetting("green", 255.0, 0.0, 255.0, "Fixed tracer green channel");
        addNumberSetting("blue", 255.0, 0.0, 255.0, "Fixed tracer blue channel");
        me.mrhakan.agalarhack.services.RainbowColors.registerSettings(this);
        addNumberSetting("alpha", 180.0, 32.0, 255.0, "Tracer alpha channel");
    }

    public List<Entity> targets() { return targets; }

    @Override
    public void onDisable() {
        service(ScannerService.class).cancel(this);
        targets = List.of();
    }

    @Override
    public void onUpdate() {
        var player = mc.player;
        if (player == null) { targets = List.of(); return; }
        EntityDiscovery.offer(this, service(ScannerService.class), ScanScheduler.Priority.NEAR,
                mc, (int) getNumberSetting("maximumTargets", 64), getNumberSetting("range", 96),
                entity -> entity != player && entity.isAlive() && group(entity) != Group.NONE
                        && enabled(group(entity)) ? entity : null,
                result -> targets = result);
    }

    /** Which configured group an entity belongs to; the first match wins. */
    public enum Group { NONE, FRIEND, PLAYER, HOSTILE, PASSIVE, ITEM }

    public static Group group(Entity entity) {
        if (entity instanceof Player) {
            var friends = AgalarHackClient.FRIEND_MANAGER;
            return friends != null && friends.isFriend(entity.getName().getString()) ? Group.FRIEND : Group.PLAYER;
        }
        if (entity instanceof ItemEntity) return Group.ITEM;
        if (entity instanceof Mob mob) {
            return mob.getType().getCategory() == MobCategory.MONSTER ? Group.HOSTILE : Group.PASSIVE;
        }
        return entity instanceof LivingEntity ? Group.PASSIVE : Group.NONE;
    }

    public boolean enabled(Group group) {
        return switch (group) {
            case FRIEND -> getBooleanSetting("friends", true);
            case PLAYER -> getBooleanSetting("players", true);
            case HOSTILE -> getBooleanSetting("hostiles", false);
            case PASSIVE -> getBooleanSetting("passives", false);
            case ITEM -> getBooleanSetting("items", false);
            case NONE -> false;
        };
    }

    /** Group colours reuse familiar conventions: friends green, hostiles red, items yellow. */
    /**
     * @param phase where along the rainbow cycle this line sits, so a screen of tracers spreads
     *              across the hues instead of flashing in unison; ignored unless rainbow is on
     */
    public int colorFor(Group group, double phase) {
        int base = colorFor(group);
        if (!me.mrhakan.agalarhack.services.RainbowColors.enabled(this)) return base;
        return me.mrhakan.agalarhack.services.RainbowColors.cycle(this, System.nanoTime() / 1_000_000L, base, phase);
    }

    public int colorFor(Group group) {
        if (!getBooleanSetting("perGroupColors", true)) {
            return ((int) getNumberSetting("red", 255) << 16)
                    | ((int) getNumberSetting("green", 255) << 8)
                    | (int) getNumberSetting("blue", 255);
        }
        return switch (group) {
            case FRIEND -> 0x55FF78;
            case PLAYER -> 0x55AAFF;
            case HOSTILE -> 0xFF5555;
            case PASSIVE -> 0xC8C8C8;
            case ITEM -> 0xFFDC3C;
            case NONE -> 0xFFFFFF;
        };
    }
}
