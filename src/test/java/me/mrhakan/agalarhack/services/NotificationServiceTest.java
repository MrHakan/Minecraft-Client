package me.mrhakan.agalarhack.services;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NotificationServiceTest {
    @Test void queueExpiresDeduplicatesAndEvictsOldest() {
        AtomicLong now = new AtomicLong();
        var notices = new NotificationService(now::get);
        notices.configure(2, 1000);
        notices.publish(NotificationService.Type.INFO, "one");
        notices.publish(NotificationService.Type.INFO, "one");
        assertEquals(1, notices.visible().size());
        notices.publish(NotificationService.Type.WARNING, "two");
        notices.publish(NotificationService.Type.ERROR, "three");
        assertEquals("two", notices.visible().get(0).text());
        now.set(1000); assertTrue(notices.visible().isEmpty());
    }
}
