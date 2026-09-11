package me.mrhakan.agalarhack.ui;

import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.managers.HudLayoutManager;
import me.mrhakan.agalarhack.managers.ModuleManager;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.NotificationService;
import me.mrhakan.agalarhack.services.ThemeService;
import me.mrhakan.agalarhack.ui.hud.NotificationLayout;
import net.minecraft.client.Minecraft;

public final class NotificationHud {
    private final NotificationService notifications;
    private final ModuleManager modules;
    private int measuredWidth = 220, measuredHeight = 25;
    public NotificationHud(NotificationService notifications, ModuleManager modules) {
        this.notifications = notifications; this.modules = modules;
    }
    public int width() { return measuredWidth; }
    public int height() { return measuredHeight; }
    public void render(ClientEvents.HudRender event) {
        var module = modules.getModule("Notifications");
        var notices = notifications.visible();
        if (module == null || !module.isToggled() || notices.isEmpty()) {
            measuredWidth = 220; measuredHeight = 25;
            return;
        }
        var graphics = event.graphics();
        var font = Minecraft.getInstance().font;
        var bounds = NotificationLayout.measure(notices.stream().map(notice -> font.width(notice.text())).toList(),
                graphics.guiWidth(), graphics.guiHeight(), font.lineHeight);
        measuredWidth = bounds.width(); measuredHeight = bounds.height();
        if (bounds.rows() == 0) return;
        var layout = ClientServices.require(HudLayoutManager.class);
        var anchor = layout.get("notifications").anchor;
        boolean left = anchor == HudLayoutManager.Anchor.TOP_LEFT || anchor == HudLayoutManager.Anchor.BOTTOM_LEFT;
        int baseX = layout.resolveX("notifications", graphics.guiWidth(), bounds.width());
        int baseY = layout.resolveY("notifications", graphics.guiHeight(), bounds.height());
        var theme = ClientServices.require(ThemeService.class).current();
        long now = notifications.now();
        for (int row = 0; row < bounds.rows(); row++) {
            var notice = notices.get(row);
            int y = baseY + row * (bounds.rowHeight() + NotificationLayout.GAP);
            double progress = Math.max(0, Math.min(1, (now - notice.created()) * theme.animationSpeed / 180.0));
            int offset = module.getBooleanSetting("animations", true) && theme.uiAnimations ? (int)((1 - progress) * 20) : 0;
            int x = baseX + (left ? -offset : offset);
            int color = switch (notice.type()) {
                case INFO -> 0xff65adff; case SUCCESS -> 0xff67d9a2; case WARNING -> 0xffffc466; case ERROR -> 0xffff6b7a;
            };
            graphics.fill(x, y, x + bounds.width(), y + bounds.rowHeight(), ClientUiTheme.PANEL);
            graphics.fill(x, y, x + 3, y + bounds.rowHeight(), color);
            graphics.text(font, font.plainSubstrByWidth(notice.text(), Math.max(1, bounds.width() - 14)),
                    x + 8, y + 8, ClientUiTheme.TEXT, true);
        }
    }
}
