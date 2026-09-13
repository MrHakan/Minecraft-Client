package me.mrhakan.agalarhack.module.player;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.client.gui.screens.DeathScreen;

/** Respawns after death once a configurable delay has passed. */
public class AutoRespawn extends Module {
    private int waited;
    /** Set once the packet is away, so a slow reply is not read as "it did not work". */
    private boolean requested;

    public AutoRespawn() {
        super("AutoRespawn", Category.PLAYER, "Respawns automatically after a configurable delay");
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

    @Override public void onEnable() { reset(); }
    @Override public void onDisable() { reset(); }
    @Override public void onDisconnect() { reset(); }

    private void reset() {
        waited = 0;
        requested = false;
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null || !(mc.gui.screen() instanceof DeathScreen)) {
            reset();
            return;
        }
        // The death screen is still up because the server has not answered yet, not because the
        // request was lost. Without this the counter simply refilled and the packet went again -
        // every `delay` ticks while the reply was in flight, and with the delay set to 0, every
        // single tick. One death, one respawn request.
        if (requested) return;
        if (waited < (int) Math.round(getNumberSetting("delay", 10))) { waited++; return; }
        waited = 0;
        requested = true;
        // respawn() sends the packet; Minecraft closes the death screen when the server replies.
        mc.player.respawn();
    }
}
