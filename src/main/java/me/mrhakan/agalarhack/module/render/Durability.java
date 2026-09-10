package me.mrhakan.agalarhack.module.render;

import java.util.List;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.ui.hud.HudInfoProvider;
import me.mrhakan.agalarhack.ui.hud.HudLine;
import net.minecraft.world.item.ItemStack;

public class Durability extends Module implements HudInfoProvider {

    public Durability() {
        super("Durability", Category.RENDER, "Shows remaining durability for the damageable item in your main hand");
    }

    @Override
    public void selfSettings() {
        addBooleanSetting("showName", true, "Include the held item's display name");
        addBooleanSetting("showPercent", true, "Include remaining durability as a percentage");
        addNumberSetting("warningPercent", 15.0, 1.0, 50.0, "Turn the HUD line red at or below this remaining percentage");
    }

    @Override
    public List<HudLine> getHudLines() {
        if (mc.player == null) {
            return List.of();
        }

        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            return List.of();
        }

        int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        int percent = (int) Math.round(remaining * 100.0 / stack.getMaxDamage());
        StringBuilder text = new StringBuilder("Durability: ");
        if (getBooleanSetting("showName", true)) {
            text.append(stack.getHoverName().getString()).append(" ");
        }
        text.append(remaining).append('/').append(stack.getMaxDamage());
        if (getBooleanSetting("showPercent", true)) {
            text.append(" (").append(percent).append("%)");
        }

        int color = percent <= getNumberSetting("warningPercent", 15.0) ? 0xFFFF5555 : 0xFFF0F0F0;
        return List.of(new HudLine(text.toString(), color));
    }
}
