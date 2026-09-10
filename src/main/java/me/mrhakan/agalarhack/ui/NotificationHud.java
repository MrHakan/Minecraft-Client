package me.mrhakan.agalarhack.ui;

import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.managers.ModuleManager;
import me.mrhakan.agalarhack.services.NotificationService;
import net.minecraft.client.Minecraft;

public final class NotificationHud {
    private final NotificationService notifications;
    private final ModuleManager modules;
    public NotificationHud(NotificationService notifications, ModuleManager modules) { this.notifications = notifications; this.modules = modules; }
    public void render(ClientEvents.HudRender event) {
        var module = modules.getModule("Notifications");
        var notices = notifications.visible();
        if (module == null || !module.isToggled() || notices.isEmpty()) return;
        var graphics = event.graphics(); var font = Minecraft.getInstance().font;
        String position = module.getStringSetting("position", "bottom_right");
        boolean left = position.endsWith("left"), top = position.startsWith("top");
        int row = 0;
        for (var notice : notices) {
            int width = Math.min(Math.max(100, font.width(notice.text()) + 18), Math.max(20, graphics.guiWidth() - 16));
            int height = font.lineHeight + 16;
            int y = top ? 8 + row * (height + 4) : graphics.guiHeight() - 8 - (row + 1) * (height + 4);
            if (y < 0 || y + height > graphics.guiHeight()) break;
            var theme=me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.services.ThemeService.class).current();
            double progress = Math.min(1, (notifications.now() - notice.created()) * theme.animationSpeed / 180.0);
            int offset = module.getBooleanSetting("animations", true) && theme.uiAnimations ? (int) ((1 - progress) * 20) : 0;
            int x = left ? 8 - offset : graphics.guiWidth() - width - 8 + offset;
            int color = switch (notice.type()) { case INFO -> 0xff65adff; case SUCCESS -> 0xff67d9a2; case WARNING -> 0xffffc466; case ERROR -> 0xffff6b7a; };
            graphics.fill(x, y, x + width, y + height, 0xe818202b);
            graphics.fill(x, y, x + 3, y + height, color);
            String text = font.plainSubstrByWidth(notice.text(), Math.max(1, width - 14));
            graphics.text(font, text, x + 8, y + 8, 0xffeeeeee, true);
            row++;
        }
    }
}
