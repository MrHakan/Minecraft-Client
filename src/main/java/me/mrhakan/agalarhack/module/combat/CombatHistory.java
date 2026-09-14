package me.mrhakan.agalarhack.module.combat;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.CombatLog;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client-only record of recent combat targets.
 *
 * <p>It reports what this client did and saw, never anything the server keeps to itself. In
 * particular it does not report damage dealt: the client is not told that, and deriving it from
 * health differences would be wrong the moment a server heals, absorbs or cancels a hit.
 */
public class CombatHistory extends Module {
    private final CombatLog log = new CombatLog(CombatLog.MAX_ENTRIES);
    private LivingEntity lastTarget;

    public CombatHistory() {
        super("CombatHistory", Category.COMBAT, "Remembers who you recently fought, from what this client observed");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("entries", 8, 1, CombatLog.MAX_ENTRIES, "Maximum remembered targets");
        addNumberSetting("forgetAfter", 120, 10, 900, "Seconds before an idle target is forgotten");
        addBooleanSetting("showTimeSince", true, "Show seconds since the last combat in the module list");
    }

    public CombatLog log() { return log; }

    @Override
    public void onEnable() {
        log.clear();
        lastTarget = null;
    }

    @Override
    public void onDisable() {
        log.clear();
        lastTarget = null;
        setDisplayName(null);
    }

    @Override public void onDisconnect() { onDisable(); }

    @Override
    public void onUpdate() {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        log.expire(now, (long) (getNumberSetting("forgetAfter", 120) * 1000));

        LivingEntity target = AgalarHackClient.TARGET_TRACKER.get(2.0);
        if (target != null && target.isAlive()) {
            // A change of target counts as a fresh engagement; the same target refreshes its entry.
            log.record(target.getName().getString(), now, target.getHealth(), target != lastTarget);
            lastTarget = target;
        } else {
            lastTarget = null;
        }
        updateDisplay(now);
    }

    private void updateDisplay(long now) {
        if (!getBooleanSetting("showTimeSince", true) || log.size() == 0) {
            setDisplayName(null);
            return;
        }
        double seconds = log.secondsSinceCombat(now);
        if (seconds < 0) { setDisplayName(null); return; }
        setDisplayName(String.format(java.util.Locale.ROOT, "CombatHistory [%.0fs ago]", seconds));
    }
}
