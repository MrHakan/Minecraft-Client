package me.mrhakan.agalarhack.services;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Bounded, client-thread notifications with a monotonic injectable clock. */
public final class NotificationService {
    public enum Type { INFO, SUCCESS, WARNING, ERROR }
    public record Notice(Type type, String text, long created, long expires) { }
    private final ArrayDeque<Notice> queue = new ArrayDeque<>();
    private final LongSupplier clock;
    private int maximum = 5;
    private boolean enabled = true;
    private long duration = 4000;
    /** Set by the client so the service itself stays free of Minecraft types and remains testable. */
    private Consumer<Notice> published = notice -> { };
    public NotificationService() { this(() -> System.nanoTime() / 1_000_000); }
    public NotificationService(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }
    /**
     * Called once for every notification that is actually accepted, after duplicate suppression
     * and while the service is enabled. Failures in the sink never reach the caller.
     */
    public void onPublished(Consumer<Notice> sink) {
        this.published = sink == null ? notice -> { } : sink;
    }

    public void configure(int maximum, long duration) {
        this.maximum = Math.max(1, Math.min(10, maximum));
        this.duration = Math.max(500, Math.min(30000, duration));
        trim();
    }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) clear();
    }
    public void publish(Type type, String text) {
        if (!enabled || text == null || text.isBlank()) return;
        long now = clock.getAsLong();
        String bounded = text.length() > 180 ? text.substring(0, 177) + "..." : text;
        Notice last = queue.peekLast();
        if (last != null && last.type() == type && last.text().equals(bounded) && now - last.created() < 500) return;
        Notice notice = new Notice(Objects.requireNonNull(type), bounded, now, now + duration);
        queue.addLast(notice);
        trim();
        try {
            published.accept(notice);
        } catch (RuntimeException ignored) {
            // A failing sink must never stop the notification itself from being shown.
        }
    }
    private void trim() { while (queue.size() > maximum) queue.removeFirst(); }
    public List<Notice> visible() {
        long now = clock.getAsLong();
        queue.removeIf(notice -> notice.expires() <= now);
        return List.copyOf(queue);
    }
    public long now() { return clock.getAsLong(); }
    public void clear() { queue.clear(); }
}
