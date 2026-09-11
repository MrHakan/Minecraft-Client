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
 * <p>Lines ChatMentions counts as a mention can be marked with a coloured arrow. The mark goes in
 * front of the line rather than recolouring the matched word inside it: styling a substring means
 * rebuilding the component tree, and getting that wrong strips the server's own colours and breaks
 * click events on links. A prefix cannot damage anything it is prepended to.
 *
 * <p>Hiding repeated lines lives on ChatFilter, where hiding belongs; this module only adds.
 */
public class BetterChat extends Module {
    public BetterChat() {
        super("BetterChat", Category.MISC, "Adds a local timestamp to each chat line, and marks mentions");
    }

    @Override
    public void selfSettings() {
        addBooleanSetting("timestamps", true, "Prefix each line with the time it reached this client");
        addBooleanSetting("seconds", false, "Include seconds in the timestamp");
        addBooleanSetting("markMentions", true, "Mark lines ChatMentions counts as a mention; needs that module on");
    }

    @Override public boolean runsWithoutWorld() { return true; }
}
