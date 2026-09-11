package me.mrhakan.agalarhack.module.misc;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

/**
 * Timestamps every chat line as it arrives.
 *
 * <p>Local display only: nothing is sent, nothing is blocked, and no other player's view changes. The
 * stamp is your own clock at the moment the line reached this client, which is the only time the
 * client actually knows — a server can send a message long after the event it describes, and this
 * does not claim otherwise.
 *
 * <p>Hiding repeated lines lives on ChatFilter, where hiding belongs; this module only adds.
 */
public class BetterChat extends Module {
    public BetterChat() {
        super("BetterChat", Category.MISC, "Adds a local timestamp to each chat line as it arrives");
        markExperimental();
    }

    @Override
    public void selfSettings() {
        addBooleanSetting("timestamps", true, "Prefix each line with the time it reached this client");
        addBooleanSetting("seconds", false, "Include seconds in the timestamp");
    }

    @Override public boolean runsWithoutWorld() { return true; }
}
