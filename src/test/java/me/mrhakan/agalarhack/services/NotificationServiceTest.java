package me.mrhakan.agalarhack.services;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NotificationServiceTest {
    @Test void disabledNotificationsClearQueueAndDiscardNewMessages() {
        var notices = new NotificationService(() -> 0);
        notices.publish(NotificationService.Type.INFO, "old");
        notices.setEnabled(false);
        notices.publish(NotificationService.Type.INFO, "hidden");
        notices.setEnabled(true);
        assertTrue(notices.visible().isEmpty());
        notices.publish(NotificationService.Type.INFO, "new");
        assertEquals("new", notices.visible().getFirst().text());
    }
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

    @Test void theSinkSeesEveryAcceptedNoticeAndNoSuppressedOne() {
        long[] clock = { 0 };
        var service = new NotificationService(() -> clock[0]);
        var seen = new java.util.ArrayList<String>();
        service.onPublished(notice -> seen.add(notice.type() + ":" + notice.text()));
        service.publish(NotificationService.Type.INFO, "one");
        service.publish(NotificationService.Type.INFO, "one");
        service.publish(NotificationService.Type.ERROR, "two");
        assertEquals(java.util.List.of("INFO:one", "ERROR:two"), seen);
    }

    @Test void aDisabledServiceNotifiesNoSink() {
        var service = new NotificationService(() -> 0L);
        var seen = new java.util.ArrayList<String>();
        service.onPublished(notice -> seen.add(notice.text()));
        service.setEnabled(false);
        service.publish(NotificationService.Type.INFO, "ignored");
        assertTrue(seen.isEmpty());
    }

    @Test void aFailingSinkNeverStopsTheNotification() {
        var service = new NotificationService(() -> 0L);
        service.onPublished(notice -> { throw new IllegalStateException("sink broke"); });
        service.publish(NotificationService.Type.INFO, "still shown");
        assertEquals(1, service.visible().size());
    }

    @Test void clearingTheSinkIsSafe() {
        var service = new NotificationService(() -> 0L);
        service.onPublished(null);
        service.publish(NotificationService.Type.INFO, "fine");
        assertEquals(1, service.visible().size());
    }
}
