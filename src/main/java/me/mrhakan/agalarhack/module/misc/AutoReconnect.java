package me.mrhakan.agalarhack.module.misc;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Reconnects to the last multiplayer server from the vanilla disconnect screen. */
public class AutoReconnect extends Module {
    private ServerData lastServer;
    private boolean armed;
    private int ticksRemaining;
    private int attempts;
    private Object observedDisconnectScreen;
    private int displayedSeconds = -1;

    public AutoReconnect() {
        super("AutoReconnect", Category.MISC, "Reconnects to the last multiplayer server after an unexpected disconnect");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("delaySeconds", 5.0, 1.0, 60.0, "Delay before each reconnect attempt");
        addNumberSetting("maxAttempts", 5.0, 1.0, 50.0, "Maximum reconnect attempts before giving up");
    }

    @Override
    public boolean runsWithoutWorld() { return true; }

    @Override
    public void onUpdate() {
        if (mc.level != null && mc.getCurrentServer() != null) {
            lastServer = copyServer(mc.getCurrentServer());
            clearSchedule();
            attempts = 0;
            setDisplayName(null);
            return;
        }
        if (!(mc.gui.screen() instanceof DisconnectedScreen disconnected)) {
            if (!(mc.gui.screen() instanceof ConnectScreen)) clearSchedule();
            setDisplayName(null);
            return;
        }
        if (lastServer == null) {
            setDisplayName("AutoReconnect [no server]");
            return;
        }
        if (observedDisconnectScreen != disconnected) {
            observedDisconnectScreen = disconnected;
            ticksRemaining = delayTicks();
            displayedSeconds = -1;
            armed = true;
        }
        if (!armed) return;

        int maxAttempts = (int) Math.round(getNumberSetting("maxAttempts", 5.0));
        if (attempts >= maxAttempts) {
            armed = false;
            setDisplayName("AutoReconnect [stopped " + attempts + "/" + maxAttempts + "]");
            return;
        }
        if (ticksRemaining > 0) {
            ticksRemaining--;
            int seconds = Math.max(1, (ticksRemaining + 19) / 20);
            if (seconds != displayedSeconds) {
                displayedSeconds = seconds;
                setDisplayName("AutoReconnect [" + seconds + "s · " + attempts + "/" + maxAttempts + "]");
            }
            return;
        }

        attempts++;
        armed = false;
        displayedSeconds = -1;
        service(me.mrhakan.agalarhack.services.NotificationService.class).publish(
                me.mrhakan.agalarhack.services.NotificationService.Type.INFO, "Reconnect attempt " + attempts);
        setDisplayName("AutoReconnect [attempt " + attempts + "/" + maxAttempts + "]");
        try {
            ServerAddress address = ServerAddress.parseString(lastServer.ip);
            ConnectScreen.startConnecting(disconnected, mc, address, lastServer, false, null);
        } catch (RuntimeException e) {
            ticksRemaining = delayTicks();
            armed = true;
        }
    }

    @Override public void onDisconnect() { }

    private int delayTicks() {
        return Math.max(20, (int) Math.round(getNumberSetting("delaySeconds", 5.0) * 20.0));
    }
    private ServerData copyServer(ServerData source) {
        ServerData copy = new ServerData(source.name, source.ip, source.type());
        copy.copyFrom(source);
        return copy;
    }
    private void clearSchedule() {
        armed = false;
        ticksRemaining = 0;
        displayedSeconds = -1;
        observedDisconnectScreen = null;
    }
    @Override public void onDisable() {
        clearSchedule();
        attempts = 0;
        setDisplayName(null);
    }
}
