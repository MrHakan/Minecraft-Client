package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import java.util.List;
import me.mrhakan.agalarhack.managers.TargetPolicyManager;
import me.mrhakan.agalarhack.services.ScannerService;
import me.mrhakan.agalarhack.services.scanning.ScanScheduler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Tick-sampled, bounded entity discovery; render callbacks consume an immutable snapshot. */
public class EntityESP extends Module {
    private List<LivingEntity> targets = List.of();
    public List<LivingEntity> targets() { return targets; }
    @Override public void onDisable() { service(ScannerService.class).cancel(this); targets = List.of(); }
    @Override public void onUpdate() {
        var player = mc.player;
        if (player == null) { targets = List.of(); return; }
        var policy = service(TargetPolicyManager.class);
        boolean respectPolicy = getBooleanSetting("respectTargetPolicy", true);
        me.mrhakan.agalarhack.services.EntityDiscovery.offer(this, service(ScannerService.class),
                ScanScheduler.Priority.NEAR, mc, (int) getNumberSetting("maximumTargets", 256),
                getNumberSetting("range", 96),
                entity -> {
                    if (!(entity instanceof LivingEntity living) || living == player || !living.isAlive()) return null;
                    boolean allowed = respectPolicy ? policy.allows(living)
                            : getBooleanSetting(living instanceof Player ? "players" : "mobs", true);
                    return allowed ? living : null;
                },
                result -> targets = result);
    }
    public EntityESP() {
        super("ESP", Category.RENDER, "Draws configurable boxes, tracers and labels around valid living entities");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("range", 96.0, 8.0, 256.0, "Maximum render distance in blocks");
        addNumberSetting("maximumTargets", 256, 16, 512, "Maximum rendered entities; retain nearest among at most 4096 observations per tick");
        addNumberSetting("labelRange", 64, 0, 256, "Maximum label distance, also limited by ESP range");
        addBooleanSetting("respectTargetPolicy", true, "Use the shared global target policy as the entity filter");
        addBooleanSetting("players", true, "Show players when target policy filtering is disabled");
        addBooleanSetting("mobs", true, "Show non-player living entities when target policy filtering is disabled");
        addBooleanSetting("boxes", true, "Draw world-space bounding boxes");
        addBooleanSetting("tracers", false, "Draw a line from the camera toward each visible ESP target");
        addBooleanSetting("labels", true, "Draw the entity name above ESP targets");
        addBooleanSetting("showDistance", true, "Include distance in ESP labels");
        addBooleanSetting("showHealth", false, "Include current health in ESP labels");
        addBooleanSetting("friendColors", true, "Render players in the local friend list with a dedicated color");
        addBooleanSetting("teamColors", true, "Use the scoreboard team color for allied players");
        addBooleanSetting("distanceFade", true, "Fade ESP geometry as targets approach the configured range limit");
        addNumberSetting("fadeStart", 32.0, 0.0, 256.0, "Distance in blocks where fading begins");
        addNumberSetting("minimumAlpha", 64.0, 0.0, 255.0, "Minimum geometry alpha at maximum range");
        addNumberSetting("friendRed", 85.0, 0.0, 255.0, "Friend overlay red channel");
        addNumberSetting("friendGreen", 255.0, 0.0, 255.0, "Friend overlay green channel");
        addNumberSetting("friendBlue", 120.0, 0.0, 255.0, "Friend overlay blue channel");
        addNumberSetting("red", 85.0, 0.0, 255.0, "Default overlay red channel");
        addNumberSetting("green", 170.0, 0.0, 255.0, "Default overlay green channel");
        addNumberSetting("blue", 255.0, 0.0, 255.0, "Default overlay blue channel");
        me.mrhakan.agalarhack.services.RainbowColors.registerSettings(this);
        addNumberSetting("alpha", 230.0, 32.0, 255.0, "Default overlay alpha channel");
    }
}
