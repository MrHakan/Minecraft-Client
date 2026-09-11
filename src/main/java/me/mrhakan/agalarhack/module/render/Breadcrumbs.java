package me.mrhakan.agalarhack.module.render;

import java.util.List;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.BreadcrumbTrail;
import net.minecraft.client.multiplayer.ClientLevel;

/** Draws the path the player has walked, cleared on disconnect and dimension change. */
public class Breadcrumbs extends Module {
    private final BreadcrumbTrail trail = new BreadcrumbTrail(1024, 1.0);
    private ClientLevel recordedLevel;

    public Breadcrumbs() {
        super("Breadcrumbs", Category.RENDER, "Records and draws the path you have walked");
        markExperimental();
    }

    @Override
    public void selfSettings() {
        addNumberSetting("maxPoints", 1024, 16, BreadcrumbTrail.MAX_POINTS, "Maximum recorded path points");
        addNumberSetting("minDistance", 1.0, 0.25, 8.0, "Minimum distance between recorded points");
        addNumberSetting("duration", 0, 0, 3600, "Seconds a point is kept; 0 keeps them until the cap");
        addNumberSetting("red", 120.0, 0.0, 255.0, "Trail red channel");
        addNumberSetting("green", 220.0, 0.0, 255.0, "Trail green channel");
        addNumberSetting("blue", 255.0, 0.0, 255.0, "Trail blue channel");
        addNumberSetting("alpha", 200.0, 32.0, 255.0, "Trail alpha channel");
        addBooleanSetting("fade", true, "Fade older points");
    }

    public List<BreadcrumbTrail.Point> points() { return trail.snapshot(); }

    @Override public void onEnable() { clear(); }
    @Override public void onDisable() { clear(); }
    @Override public void onDisconnect() { clear(); }

    /** A dimension change makes the recorded coordinates meaningless, so the trail starts over. */
    @Override
    public void onWorldChanged(boolean ready) {
        clear();
        super.onWorldChanged(ready);
    }

    private void clear() {
        trail.clear();
        recordedLevel = null;
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null) return;
        if (recordedLevel != mc.level) { trail.clear(); recordedLevel = mc.level; }
        trail.configure((int) getNumberSetting("maxPoints", 1024), getNumberSetting("minDistance", 1.0));
        long now = System.currentTimeMillis();
        trail.sample(mc.player.getX(), mc.player.getY(), mc.player.getZ(), now);
        long duration = (long) (getNumberSetting("duration", 0) * 1000);
        trail.expire(now, duration);
    }
}
