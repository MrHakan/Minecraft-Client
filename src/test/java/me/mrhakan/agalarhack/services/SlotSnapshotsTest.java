package me.mrhakan.agalarhack.services;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlotSnapshotsTest {
    @Test void sourceAndPublishedPayloadCannotMutateCachedState() {
        var slots = new SlotSnapshots<int[]>(6, Arrays::equals, int[]::clone);
        int[] source = {1, 0};
        var first = slots.update(5, source);
        assertNull(first.previous());
        first.current()[0] = 99;
        assertNull(slots.update(5, source));
        source[1] = 10;
        var changed = slots.update(5, source);
        assertArrayEquals(new int[]{1, 0}, changed.previous());
        assertArrayEquals(new int[]{1, 10}, changed.current());
        assertNull(slots.update(5, source));
    }
    @Test void replacementClearsPreviousPlayerAndSlotsRemainIndependent() {
        var slots = new SlotSnapshots<int[]>(6, Arrays::equals, int[]::clone);
        slots.update(0, new int[]{1});
        assertNull(slots.update(1, new int[]{2}).previous());
        slots.clear();
        assertNull(slots.update(0, new int[]{3}).previous());
        assertThrows(IndexOutOfBoundsException.class, () -> slots.update(6, new int[]{1}));
        assertThrows(IllegalArgumentException.class, () -> new SlotSnapshots<int[]>(65, Arrays::equals, int[]::clone));
    }
}
