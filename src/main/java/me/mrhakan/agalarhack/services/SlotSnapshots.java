package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;

/** Fixed-size change tracking; cached state and event payloads never share mutable values. */
public final class SlotSnapshots<T> {
    public record Change<T>(T previous, T current) { }
    private final List<T> values;
    private final BiPredicate<T, T> same;
    private final UnaryOperator<T> copy;

    public SlotSnapshots(int slots, BiPredicate<T, T> same, UnaryOperator<T> copy) {
        if (slots < 1 || slots > 64) throw new IllegalArgumentException("Invalid slot count");
        values = new ArrayList<>(Collections.nCopies(slots, null));
        this.same = Objects.requireNonNull(same);
        this.copy = Objects.requireNonNull(copy);
    }
    public Change<T> update(int slot, T current) {
        Objects.requireNonNull(current);
        T previous = values.get(slot);
        if (previous != null && same.test(previous, current)) return null;
        values.set(slot, copy.apply(current));
        return new Change<>(previous == null ? null : copy.apply(previous), copy.apply(current));
    }
    public void clear() { Collections.fill(values, null); }
}
