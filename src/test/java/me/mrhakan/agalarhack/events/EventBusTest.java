package me.mrhakan.agalarhack.events;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {
    @Test void priorityAndFailureIsolation() {
        List<String> calls = new ArrayList<>();
        EventBus bus = new EventBus((owner, error) -> calls.add(owner));
        bus.subscribe(String.class, "low", 0, event -> calls.add("low"));
        bus.subscribe(String.class, "bad", 10, event -> { throw new IllegalStateException(); });
        bus.post("first"); bus.post("second");
        assertEquals(List.of("bad", "low", "low"), calls);
    }
    @Test void closingDuringDispatchSkipsListener() {
        EventBus bus = new EventBus((owner, error) -> fail(error));
        var later = bus.subscribe(String.class, "later", 0, event -> fail("closed"));
        bus.subscribe(String.class, "first", 10, event -> later.close());
        bus.post("event"); later.close();
    }
    @Test void registrationsDuringDispatchWaitForNextPost() {
        List<String> calls = new ArrayList<>();
        EventBus bus = new EventBus((owner, error) -> fail(error));
        bus.subscribe(String.class, "first", 0, event -> bus.subscribe(String.class, "new", 0, calls::add));
        bus.post("one"); assertTrue(calls.isEmpty());
        bus.post("two"); assertEquals(List.of("two"), calls);
    }
}
