package me.mrhakan.agalarhack.module.combat;

import java.util.Locale;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.CriticalHits;

/**
 * Says whether your next hit would land as a critical, and how charged it is.
 *
 * <p>Purely informational, and deliberately so: the roadmap asks for critical information and rules
 * out the packet chains clients usually use to force one. Everything here is a fact the client
 * already has about its own player — the attack charge it draws on the crosshair anyway, and
 * Minecraft's own 26.2 crit rule applied to the player's own state. It changes nothing, sends
 * nothing, and touches no input.
 *
 * <p>The charge figure is the one most worth watching: a fully charged hit does several times the
 * damage of a spammed one, and vanilla shows it only as a small bar that is easy to miss mid-fight.
 */
public class CritInfo extends Module {
    public CritInfo() {
        super("CritInfo", Category.COMBAT, "Shows attack charge and whether the next hit would be a critical; informational only");
        markExperimental();
    }

    @Override
    public void selfSettings() {
        addBooleanSetting("showCharge", true, "Show the attack charge as a percentage");
        addNumberSetting("readyAt", 100.0, 50.0, 100.0, "Charge percentage counted as ready");
    }

    @Override public void onDisable() { setDisplayName(null); }
    @Override public void onDisconnect() { setDisplayName(null); }

    /** True when an attack landing this instant would be a critical hit. */
    public boolean critReady() {
        var player = mc.player;
        if (player == null) return false;
        return CriticalHits.wouldCrit(new CriticalHits.State(player.fallDistance, player.onGround(),
                player.onClimbable(), player.isInWater(), player.isMobilityRestricted(),
                player.isPassenger(), player.isSprinting()));
    }

    /** Attack charge as a percentage; 100 means a fully recovered swing. */
    public double chargePercent() {
        // 0.5f is the partial-tick vanilla itself passes when drawing the indicator.
        return mc.player == null ? 0 : Math.max(0, Math.min(1, mc.player.getAttackStrengthScale(0.5f))) * 100.0;
    }

    public boolean chargeReady() {
        return chargePercent() >= getNumberSetting("readyAt", 100.0);
    }

    /** The HUD line, also used as the module's list suffix so it is readable without a widget. */
    public String line() {
        if (mc.player == null) return "Crit --";
        StringBuilder text = new StringBuilder();
        if (getBooleanSetting("showCharge", true)) {
            text.append(String.format(Locale.ROOT, "Charge %.0f%%", chargePercent()));
        }
        if (text.length() > 0) text.append("  ");
        text.append(critReady() ? "CRIT" : "no crit");
        return text.toString();
    }

    @Override
    public void onUpdate() {
        if (mc.player == null) { setDisplayName(null); return; }
        setDisplayName(critReady() && chargeReady() ? "CritInfo [ready]" : null);
    }
}
