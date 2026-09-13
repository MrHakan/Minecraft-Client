package me.mrhakan.agalarhack.services;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Hides a chat line that just said the same thing.
 *
 * <p>The usual answer is to collapse repeats into the previous line with an "x3" counter, which means
 * reaching into chat history and rewriting a message already drawn. Vetoing the duplicate instead
 * achieves the thing that actually matters — the spam stops — through the veto the client already
 * has, with no mixin and nothing rewritten.
 *
 * <p>A window rather than only the immediately preceding line, because two spammers alternating
 * defeats a window of one, and that is the case people actually complain about.
 *
 * <p>Comparison ignores case and collapses runs of whitespace, since padding is the cheapest way to
 * make two identical lines look different. It does <em>not</em> ignore anything else: a message that
 * differs by one character is a different message, and guessing otherwise would hide real chat.
 *
 * <p>Free of Minecraft types so the window and the normalising are unit tested directly.
 */
public final class RepeatSuppressor {
    public static final int MAX_WINDOW = 32;

    private final Deque<String> recent = new ArrayDeque<>();
    private int window = 1;

    /**
     * @param window how many recent lines to compare against; clamped to [1, {@value #MAX_WINDOW}]
     * @return true when this line repeats one already seen and should be hidden
     */
    public boolean isRepeat(String message, int window) {
        int size = Math.max(1, Math.min(MAX_WINDOW, window));
        if (size != this.window) {
            this.window = size;
            while (recent.size() > size) recent.removeFirst();
        }
        String key = normalise(message);
        if (key.isEmpty()) return false;
        if (recent.contains(key)) return true;
        recent.addLast(key);
        while (recent.size() > size) recent.removeFirst();
        return false;
    }

    /** A new connection is a new conversation; carrying the last server's lines across would hide chat. */
    public void reset() {
        recent.clear();
    }

    public int remembered() {
        return recent.size();
    }

    /** Case and padding only. Anything more aggressive starts hiding messages that differ. */
    static String normalise(String message) {
        if (message == null) return "";
        return message.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
