package me.mrhakan.agalarhack.module.render;

import java.util.ArrayList;
import java.util.List;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.ui.hud.HudInfoProvider;
import me.mrhakan.agalarhack.ui.hud.HudLine;
import net.minecraft.world.item.ItemStack;

public class Durability extends Module implements HudInfoProvider {
    private final List<HudLine> hudLines = new ArrayList<>(2);

    public Durability() {
        super("Durability", Category.RENDER, "Shows remaining durability for equipped damageable items");
    }

    @Override
    public void selfSettings() {
        addBooleanSetting("showName", true, "Include the item's display name");
        addBooleanSetting("showPercent", true, "Include remaining durability as a percentage");
        addBooleanSetting("showOffhand", true, "Show a second line for a damageable offhand item");
        addNumberSetting("warningPercent", 15.0, 1.0, 50.0, "Turn the HUD line red at or below this remaining percentage");
    }

    @Override
    public List<HudLine> getHudLines() {
        hudLines.clear();
        if (mc.player == null) {
            return hudLines;
        }

        appendLine(mc.player.getMainHandItem(), "Main");
        if (getBooleanSetting("showOffhand", true)) {
            appendLine(mc.player.getOffhandItem(), "Offhand");
        }
        return hudLines;
    }

    private void appendLine(ItemStack stack, String hand) {
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            return;
        }

        int maxDamage = stack.getMaxDamage();
        int remaining = Math.max(0, maxDamage - stack.getDamageValue());
        int percent = (int) Math.round(remaining * 100.0 / maxDamage);
        StringBuilder text = new StringBuilder("Durability [").append(hand).append("]: ");
        if (getBooleanSetting("showName", true)) {
            text.append(stack.getHoverName().getString()).append(' ');
        }
        text.append(remaining).append('/').append(maxDamage);
        if (getBooleanSetting("showPercent", true)) {
            text.append(" (").append(percent).append("%)");
        }

        int color = percent <= getNumberSetting("warningPercent", 15.0) ? 0xFFFF5555 : 0xFFF0F0F0;
        hudLines.add(new HudLine(text.toString(), color));
    }
}
