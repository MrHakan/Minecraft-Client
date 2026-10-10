package me.mrhakan.agalarhack.services.scanning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * The order the shared entity walk reads entities in once there are more than it may observe.
 *
 * <p>The walk inspects at most a fixed number of entities a tick. Read in the render list's own
 * order, which is arrival order, the ones it inspects are simply the oldest: on a server where the
 * nearest entities arrived last, a measured crowd of 5,000 left ESP with 256 targets of which none
 * was among the true nearest 256. Ordered here, the inspected prefix is the nearest instead.
 *
 * <p>One pass works out every entity's squared distance and its band; bands split the squared range
 * into equal parts, so no square root is taken and the sort is a counting sort, linear in the crowd.
 * Within a band, entities keep their list order. Entities beyond {@code range} are left out, since no
 * subscriber could keep them.
 *
 * <p>Kept free of Minecraft types so the ordering, the limit and the range are unit tested directly.
 */
public final class NearestFirstOrder {
    /** Bands across the squared range. Enough that one band never holds a subscriber's whole result. */
    static final int BANDS = 256;

    private NearestFirstOrder() {
    }

    /**
     * @param range the furthest any subscriber looks; entities beyond it are dropped
     * @param limit how many to return at most
     * @return at most {@code limit} entities within {@code range}, nearest band first
     */
    public static <E> List<E> of(Iterable<E> entities, ToDoubleFunction<E> distanceSquared, double range, int limit) {
        double rangeSquared = range * range;
        if (limit <= 0 || !(rangeSquared > 0)) return List.of();
        List<E> kept = new ArrayList<>();
        int[] bands = new int[64];
        int[] counts = new int[BANDS];
        for (E entity : entities) {
            double distance = distanceSquared.applyAsDouble(entity);
            if (!(distance <= rangeSquared)) continue;
            int band = Math.min(BANDS - 1, (int) (distance / rangeSquared * BANDS));
            if (kept.size() == bands.length) bands = Arrays.copyOf(bands, bands.length * 2);
            bands[kept.size()] = band;
            kept.add(entity);
            counts[band]++;
        }
        int[] next = new int[BANDS];
        for (int band = 1; band < BANDS; band++) next[band] = next[band - 1] + counts[band - 1];
        Object[] ordered = new Object[kept.size()];
        for (int index = 0; index < kept.size(); index++) ordered[next[bands[index]]++] = kept.get(index);
        int size = Math.min(limit, ordered.length);
        List<E> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            @SuppressWarnings("unchecked")
            E entity = (E) ordered[index];
            result.add(entity);
        }
        return result;
    }
}
