package me.mrhakan.agalarhack.ui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Tracks screen ancestry so opening a child preserves its parent's edit session. */
public final class UiSession<T> {
    private final Function<T, T> parent;
    private final Consumer<T> abandoned;
    private List<T> active = List.of();

    public UiSession(Function<T, T> parent, Consumer<T> abandoned) {
        this.parent = Objects.requireNonNull(parent);
        this.abandoned = Objects.requireNonNull(abandoned);
    }

    public void transition(T screen) {
        List<T> next = new ArrayList<>();
        for (T current = screen; current != null; current = parent.apply(current)) {
            if (next.size() >= 32 || containsIdentity(next, current)) {
                throw new IllegalArgumentException("Invalid screen ancestry");
            }
            next.add(current);
        }
        List<T> previous = active;
        active = next;
        for (T old : previous) {
            if (!containsIdentity(next, old)) abandoned.accept(old);
        }
    }

    private static <T> boolean containsIdentity(List<T> items, T value) {
        for (T item : items) if (item == value) return true;
        return false;
    }
}
