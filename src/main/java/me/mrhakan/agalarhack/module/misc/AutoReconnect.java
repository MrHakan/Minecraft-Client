package me.mrhakan.agalarhack.module.misc;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Reconnects to the last multiplayer server after an unexpected disconnect. */
public class AutoReconnect extends Module {
    private ServerData lastServer;
    private boolean armed;
    private int ticksRemaining;
    private int attempts;

    public AutoReconnect() {
        super("AutoReconnect", Category.MISC, "Reconnects to the last multiplayer server after a disconnect");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("delaySeconds", 5.0, 1.0, 60.0, "Delay before each reconnect attempt");
        addNumberSetting("maxAttempts", 5.0, 1.0, 50.0, "Maximum reconnect attempts before giving up");
    }

    @Override
    public boolean runsWithoutWorld() {
        return true;
    }

    @Override
    public void onUpdate() {
        if (mc.level != null && mc.getCurrentServer() != null) {
            lastServer = mc.getCurrentServer();
            armed = false;
            attempts = 0;
            setDisplayName(null);
            return;
        }

        if (!armed || lastServer == null) {
            setDisplayName(null);
            return;
        }

        int maxAttempts = (int) Math.round(getNumberSetting("maxAttempts", 5.0));
        if (attempts >= maxAttempts) {
            armed = false;
            setDisplayName("AutoReconnect [stopped]");
            return;
        }

        if (ticksRemaining > 0) {
            ticksRemaining--;
            setDisplayName("AutoReconnect [" + Math.max(1, (int) Math.ceil(ticksRemaining / 20.0)) + "s]");
            return;
        }

        attempts++;
        armed = false;
        setDisplayName("AutoReconnect [attempt " + attempts + "]");
        ServerAddress address = ServerAddress.parseString(lastServer.ip);
        ConnectScreen.startConnecting(mc.gui.screen(), mc, address, lastServer, false, null);
    }

    @Override
    public void onDisconnect() {
        if (lastServer == null) {
            return;
        }
        int maxAttempts = (int) Math.round(getNumberSetting("maxAttempts", 5.0));
        if (attempts >= maxAttempts) {
            armed = false;
            return;
        }
        ticksRemaining = (int) Math.round(getNumberSetting("delaySeconds", 5.0) * 20.0);
        armed = true;
    }

    @Override
    public void onDisable() {
        armed = false;
        ticksRemaining = 0;
        attempts = 0;
        setDisplayName(null);
    }
}
