package me.mrhakan.agalarhack.events;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Synchronous, client-thread-only typed dispatch. Higher priorities run first.
 * Subscriptions added during dispatch run on the next post; closed listeners never run.
 * A failing listener is detached before reporting it, preventing repeated error floods.
 */
public final class EventBus {
    private final Map<Class<?>, List<Listener<?>>> listeners = new HashMap<>();
    private final BiConsumer<String, RuntimeException> errors;

    public EventBus(BiConsumer<String, RuntimeException> errors) {
        this.errors = Objects.requireNonNull(errors);
    }

    public <T> Subscription subscribe(Class<T> type, String owner, int priority, Consumer<T> callback) {
        Objects.requireNonNull(type);
        Listener<T> listener = new Listener<>(Objects.requireNonNull(owner), priority, Objects.requireNonNull(callback));
        List<Listener<?>> updated = new ArrayList<>(listeners.getOrDefault(type, List.of()));
        updated.add(listener);
        updated.sort(Comparator.comparingInt((Listener<?> value) -> value.priority).reversed());
        listeners.put(type, List.copyOf(updated));
        return () -> {
            listener.active = false;
            List<Listener<?>> remaining = new ArrayList<>(listeners.getOrDefault(type, List.of()));
            remaining.remove(listener);
            if (remaining.isEmpty()) listeners.remove(type);
            else listeners.put(type, List.copyOf(remaining));
        };
    }

    /**
     * Posts a vetoable event.
     *
     * @return true when no listener vetoed it, which is the shape Fabric's ALLOW_* events expect
     */
    public boolean postAllowed(ClientEvents.ChatReceived event) {
        post(event);
        return !event.vetoed();
    }

    public <T> void post(T event) {
        Objects.requireNonNull(event);
        for (Listener<?> raw : listeners.getOrDefault(event.getClass(), List.of())) {
            if (!raw.active) continue;
            try {
                @SuppressWarnings("unchecked") Listener<T> listener = (Listener<T>) raw;
                listener.callback.accept(event);
            } catch (RuntimeException failure) {
                raw.active = false;
                List<Listener<?>> remaining = new ArrayList<>(listeners.getOrDefault(event.getClass(), List.of()));
                remaining.remove(raw);
                if (remaining.isEmpty()) listeners.remove(event.getClass());
                else listeners.put(event.getClass(), List.copyOf(remaining));
                // Do not allow a broken error reporter to suppress unrelated listeners.
                try { errors.accept(raw.owner, failure); }
                catch (RuntimeException reportingFailure) { failure.addSuppressed(reportingFailure); }
            }
        }
    }

    public interface Subscription extends AutoCloseable {
        @Override void close();
    }

    private static final class Listener<T> {
        final String owner;
        final int priority;
        final Consumer<T> callback;
        boolean active = true;
        Listener(String owner, int priority, Consumer<T> callback) {
            this.owner = owner; this.priority = priority; this.callback = callback;
        }
    }
}
