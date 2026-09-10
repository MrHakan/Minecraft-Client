package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.services.NotificationService;

public final class Notifications extends Module {
    private String lastPosition;
    public Notifications() { super("Notifications", Category.RENDER, "Bounded first-party status and error toasts"); }
    @Override public void selfSettings() {
        settings.setSetting("enabled", true);
        addNumberSetting("duration", 4, 0.5, 30, "Seconds each new notification remains visible");
        addNumberSetting("maximum", 5, 1, 10, "Maximum queued notifications");
        addChoiceSetting("position", "bottom_right", "Default corner; HUD editor can override placement", "bottom_right", "top_right", "bottom_left", "top_left");
        addBooleanSetting("animations", true, "Ease notifications into view");
        addBooleanSetting("moduleToggles", true, "Notify when a module is enabled or disabled");
    }
    @Override public boolean runsWithoutWorld() { return true; }
    @Override public void onUpdate() {
        String position=getStringSetting("position","bottom_right");
        if(lastPosition!=null && !lastPosition.equals(position)) {
            var layout=service(me.mrhakan.agalarhack.managers.HudLayoutManager.class);
            layout.get("notifications").anchor=me.mrhakan.agalarhack.managers.HudLayoutManager.Anchor.valueOf(position.toUpperCase(java.util.Locale.ROOT));
            layout.save();
        }
        lastPosition=position;
        service(NotificationService.class).configure((int) getNumberSetting("maximum", 5), (long) (getNumberSetting("duration", 4) * 1000));
    }
}
