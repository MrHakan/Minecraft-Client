package me.mrhakan.agalarhack.module.player;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.client.gui.screens.DeathScreen;

/** Respawns after death once a configurable delay has passed. */
public class AutoRespawn extends Module {
    private int waited;

    public AutoRespawn() {
        super("AutoRespawn", Category.PLAYER, "Respawns automatically after a configurable delay");
        markExperimental();
    }

    @Override
    public void selfSettings() {
        addNumberSetting("delay", 10, 0, 200, "Ticks to wait on the death screen before respawning");
    }

    /**
     * The module manager stops ticking modules once the player is no longer alive, which is exactly
     * when this one has work to do. Its own guards below replace that check.
     */
    @Override public boolean runsWithoutWorld() { return true; }

    @Override public void onEnable() { waited = 0; }
    @Override public void onDisable() { waited = 0; }
    @Override public void onDisconnect() { waited = 0; }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null || !(mc.gui.screen() instanceof DeathScreen)) {
            waited = 0;
            return;
        }
        if (waited < (int) Math.round(getNumberSetting("delay", 10))) { waited++; return; }
        waited = 0;
        // respawn() sends the packet; Minecraft closes the death screen when the server replies.
        mc.player.respawn();
    }
}
